package com.routemate.voice.infrastructure;
import com.fasterxml.jackson.databind.*;
import com.routemate.voice.domain.VoiceGateway;
import com.routemate.room.domain.RoomFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Documented LiveKit HS256 grants and JSON/Twirp RoomService protocol. */
@Component
class LiveKitGateway implements VoiceGateway {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LiveKitGateway.class);
    private final String server,url,key,secret;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    LiveKitGateway(ObjectMapper json,@Value("${routemate.livekit.server-url:}")String server,
        @Value("${routemate.livekit.public-url:}")String url,@Value("${routemate.livekit.api-key:}")String key,
        @Value("${routemate.livekit.api-secret:}")String secret){
        this.json=json;this.server=server.replaceAll("/$","");this.url=url;this.key=key;this.secret=secret;
    }
    private RoomFailure unavailable(String method,String reason,Integer status,String diagnostic){
        // Only controlled field names, status codes and exception class names belong here.
        // Never log configuration values, request headers, JWTs or upstream response bodies.
        log.warn("LiveKit request failed: method={} reason={} httpStatus={} diagnostic={}",method,reason,status==null?"none":status,diagnostic);
        return new RoomFailure("VOICE_UNAVAILABLE");
    }
    private void configured(String method){
        var missing=new ArrayList<String>();
        if(server.isBlank())missing.add("LIVEKIT_SERVER_URL");
        if(url.isBlank())missing.add("LIVEKIT_PUBLIC_URL");
        if(key.isBlank())missing.add("LIVEKIT_API_KEY");
        if(secret.isBlank())missing.add("LIVEKIT_API_SECRET");
        if(!missing.isEmpty())throw unavailable(method,"MISSING_CONFIGURATION",null,String.join(",",missing));
        try {
            URI endpoint=URI.create(server),client=URI.create(url);
            if(!Set.of("http","https").contains(endpoint.getScheme())||!Set.of("ws","wss").contains(client.getScheme())||
                endpoint.getHost()==null||client.getHost()==null||endpoint.getUserInfo()!=null||client.getUserInfo()!=null||
                endpoint.getQuery()!=null||client.getQuery()!=null||endpoint.getFragment()!=null||client.getFragment()!=null)
                throw new IllegalArgumentException();
        } catch(Exception ignored){throw unavailable(method,"INVALID_URL",null,"LIVEKIT_SERVER_URL,LIVEKIT_PUBLIC_URL");}
    }
    private String b64(byte[] data){return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
    private String jwt(String identity,Map<String,Object> grants,Instant now){
        try{
            String unsigned=b64(json.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")))+"."+
                b64(json.writeValueAsBytes(Map.of("iss",key,"sub",identity,"nbf",now.minusSeconds(5).getEpochSecond(),
                "exp",now.plusSeconds(60).getEpochSecond(),"video",grants)));
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return unsigned+"."+b64(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw unavailable("SignToken","SIGNING_ERROR",null,e.getClass().getSimpleName());}
    }
    private JsonNode call(String method,String room,Map<String,Object> body){
        configured(method);
        try{
            String token=jwt("routemate-server",Map.of("room",room,"roomAdmin",true,"roomCreate",true,"roomList",true),Instant.now());
            var request=HttpRequest.newBuilder(URI.create(server+"/twirp/livekit.RoomService/"+method)).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404&&(method.equals("RemoveParticipant")||method.equals("DeleteRoom")))return json.createObjectNode();
            if(response.statusCode()!=200)throw unavailable(method,"HTTP_ERROR",response.statusCode(),"none");
            return json.readTree(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable(method,"INTERRUPTED",null,e.getClass().getSimpleName());}
        catch(RoomFailure e){throw e;}
        catch(HttpTimeoutException e){throw unavailable(method,"TIMEOUT",null,e.getClass().getSimpleName());}
        catch(JsonProcessingException e){throw unavailable(method,"INVALID_RESPONSE",null,e.getClass().getSimpleName());}
        catch(IOException e){throw unavailable(method,"IO_ERROR",null,e.getClass().getSimpleName());}
        catch(Exception e){throw unavailable(method,"UNEXPECTED_ERROR",null,e.getClass().getSimpleName());}
    }
    public Grant issue(UUID room,int capacity,UUID identity,Instant now){
        configured("CreateRoom");String name="rmv-"+room;
        call("CreateRoom",name,Map.of("name",name,"empty_timeout",60,"max_participants",capacity));
        return new Grant(url,jwt(identity.toString(),Map.of("room",name,"roomJoin",true,"canSubscribe",true,"canPublish",true,
            "canPublishSources",List.of("microphone"),"canPublishData",false),now),now.plusSeconds(60));
    }
    public void reconcile(PermissionGuard permissions){
        if(server.isBlank()||key.isBlank()||secret.isBlank()||url.isBlank())return;
        for(var room:call("ListRooms","",Map.of()).path("rooms")){
            String name=room.path("name").asText();if(!name.startsWith("rmv-"))continue;
            if(permissions.deleteIfEmpty(name,()->call("DeleteRoom",name,Map.of("room",name))))continue;
            for(var participant:call("ListParticipants",name,Map.of("room",name)).path("participants")){
                String identity=participant.path("identity").asText();
                permissions.removeIfDenied(name,identity,()->call("RemoveParticipant",name,Map.of("room",name,"identity",identity)));
            }
        }
    }
}
