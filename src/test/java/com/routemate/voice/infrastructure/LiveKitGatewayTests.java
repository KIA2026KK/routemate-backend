package com.routemate.voice.infrastructure;
import com.fasterxml.jackson.databind.*;
import com.routemate.room.domain.RoomFailure;
import com.routemate.voice.domain.VoiceGateway;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LiveKitGatewayTests {
    private final ObjectMapper json=new ObjectMapper();
    private LiveKitGateway gateway(HttpServer server){return new LiveKitGateway(json,"http://127.0.0.1:"+server.getAddress().getPort(),"ws://127.0.0.1:7880","test-key","test-secret");}
    @Test void signsShortLivedAudioOnlyRoomScopedTokens()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> requests=new CopyOnWriteArrayList<>();
        server.createContext("/",ex->{requests.add(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));ex.sendResponseHeaders(200,2);ex.getResponseBody().write("{}".getBytes());ex.close();});server.start();
        try{
            UUID room=UUID.randomUUID(),identity=UUID.randomUUID();Instant now=Instant.now();
            var result=gateway(server).issue(room,3,identity,now);String[] pieces=result.token().split("\\.");
            var claims=json.readTree(Base64.getUrlDecoder().decode(pieces[1]));
            assertThat(claims.path("sub").asText()).isEqualTo(identity.toString());
            assertThat(claims.path("exp").asLong()-now.getEpochSecond()).isEqualTo(60);
            assertThat(claims.path("video").path("room").asText()).isEqualTo("rmv-"+room);
            assertThat(claims.path("video").path("canPublishSources").get(0).asText()).isEqualTo("microphone");
            assertThat(claims.path("video").path("canPublishData").asBoolean()).isFalse();
            assertThat(claims.path("video").has("roomAdmin")).isFalse();
            var hmac=Mac.getInstance("HmacSHA256");hmac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            assertThat(Base64.getUrlDecoder().decode(pieces[2])).isEqualTo(hmac.doFinal((pieces[0]+"."+pieces[1]).getBytes(StandardCharsets.UTF_8)));
            assertThat(json.readTree(requests.getFirst()).path("max_participants").asInt()).isEqualTo(3);
            assertThat(result.toString()).doesNotContain(result.token());
        }finally{server.stop(0);}
    }
    @Test void removesUnauthorizedParticipantsAndLeavesOtherApplicationsUntouched()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> paths=new CopyOnWriteArrayList<>();
        server.createContext("/",ex->{
            String method=ex.getRequestURI().getPath();paths.add(method+":"+new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String body=method.endsWith("ListRooms")?"{\"rooms\":[{\"name\":\"rmv-active\"},{\"name\":\"rmv-ended\"},{\"name\":\"another-app\"}]}":
                method.endsWith("ListParticipants")?"{\"participants\":[{\"identity\":\"allowed\"},{\"identity\":\"revoked\"}]}":"{}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();
        });server.start();
        try{
            var allowed=Map.of("rmv-active",Set.of("allowed"));
            gateway(server).reconcile(new VoiceGateway.PermissionGuard(){
                public boolean deleteIfEmpty(String room,Runnable deletion){
                    if(!allowed.getOrDefault(room,Set.of()).isEmpty())return false;
                    deletion.run();return true;
                }
                public void removeIfDenied(String room,String identity,Runnable removal){
                    if(!allowed.getOrDefault(room,Set.of()).contains(identity))removal.run();
                }
            });
            assertThat(paths.stream().filter(p->p.contains("RemoveParticipant")).toList()).hasSize(1).allMatch(p->p.contains("revoked"));
            assertThat(paths.stream().filter(p->p.contains("DeleteRoom")).toList()).hasSize(1).allMatch(p->p.contains("rmv-ended"));
            assertThat(paths.stream().filter(p->!p.contains("ListRooms")).toList()).noneMatch(p->p.contains("another-app"));
        }finally{server.stop(0);}
    }
    @Test void unavailableServerAndMissingCredentialsFailClosed()throws Exception{
        var missing=new LiveKitGateway(json,"","","","");
        assertThatThrownBy(()->missing.issue(UUID.randomUUID(),3,UUID.randomUUID(),Instant.now())).isInstanceOf(RoomFailure.class);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{ex.sendResponseHeaders(503,-1);ex.close();});server.start();
        try{assertThatThrownBy(()->gateway(server).issue(UUID.randomUUID(),3,UUID.randomUUID(),Instant.now())).isInstanceOf(RoomFailure.class);}
        finally{server.stop(0);}
    }
}
