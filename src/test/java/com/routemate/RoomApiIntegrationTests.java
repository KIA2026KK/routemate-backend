package com.routemate;

import com.fasterxml.jackson.databind.*;
import com.routemate.voice.domain.VoiceGateway;
import com.routemate.voice.application.RoomVoice;
import com.routemate.room.domain.RoomFailure;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties={"spring.config.import=","DB_PASSWORD=unused","REDIS_PASSWORD=","routemate.maintenance-delay-ms=3600000"})
@AutoConfigureMockMvc
class RoomApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoomVoice voice;
    @MockitoBean VoiceGateway gateway;
    private record Driver(String token,String user){}
    private Driver driver(boolean trip)throws Exception{return driver(trip,37.55,126.97);}
    private Driver driver(boolean trip,double lat,double lng)throws Exception{
        var session=read(mvc.perform(post("/api/v1/auth/device-sessions")).andExpect(status().isCreated()));
        var d=new Driver(session.path("accessToken").asText(),session.path("userId").asText());
        request(d,"/api/v1/vehicles/me",Map.of("vin","KNA"+UUID.randomUUID().toString().replace("-","").substring(0,14),"source","PLEOS_SDK","vinAccessType","READ"))
            .andExpect(status().isOk());
        if(trip)destination(d,lat,lng);return d;
    }
    private void destination(Driver d,double lat,double lng)throws Exception{
        request(d,"/api/v1/trips",Map.of("destination",Map.of("name","목적지","latitude",lat,"longitude",lng))).andExpect(status().isOk());
    }
    private ResultActions request(Driver d,String path,Object body)throws Exception{
        var request=post(path).header("Authorization","Bearer "+d.token());
        if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }
    private JsonNode read(ResultActions action)throws Exception{
        String body=action.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return body.isBlank()?json.nullNode():json.readTree(body);
    }
    private JsonNode getAs(Driver d,String path)throws Exception{return read(mvc.perform(get(path).header("Authorization","Bearer "+d.token())).andExpect(status().isOk()));}
    private JsonNode create(Driver d,String name,boolean publicRoom,int capacity)throws Exception{
        return read(request(d,"/api/v1/rooms",Map.of("name",name,"isPublic",publicRoom,"capacity",capacity)).andExpect(status().isOk()));
    }
    private String id(JsonNode room){return room.path("id").asText();}

    @Test void requiresAuthenticationAndActiveTrip()throws Exception{
        mvc.perform(get("/api/v1/rooms/me")).andExpect(status().isUnauthorized());
        var d=driver(false);
        assertThat(getAs(d,"/api/v1/trips/me").isNull()).isTrue();
        request(d,"/api/v1/rooms",Map.of("name","방","isPublic",true,"capacity",3)).andExpect(status().isPreconditionRequired());
        mvc.perform(get("/api/v1/rooms/nearby").header("Authorization","Bearer "+d.token())).andExpect(status().isPreconditionRequired());
    }
    @Test void creationRetryAndJoinRetryPreserveMembership()throws Exception{
        var a=driver(true);var b=driver(true);var r=create(a,"동행",true,3);
        assertThat(create(a,"동행",true,3).path("id")).isEqualTo(r.path("id"));
        request(b,"/api/v1/rooms/"+id(r)+"/join",null).andExpect(status().isOk()).andExpect(jsonPath("participants").value(2));
        request(b,"/api/v1/rooms/"+id(r)+"/join",null).andExpect(status().isOk()).andExpect(jsonPath("participants").value(2));
        assertThat(getAs(b,"/api/v1/rooms/me").path("id")).isEqualTo(r.path("id"));
        assertThat(r.has("center")).isFalse();assertThat(r.has("vin")).isFalse();
    }
    @Test void preventsMultipleRoomsAndClosesEmptyRoom()throws Exception{
        var a=driver(true);var b=driver(true);var r=create(a,"첫 방",true,3);var other=create(b,"다른 방",true,3);
        request(a,"/api/v1/rooms/"+id(other)+"/join",null).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ALREADY_IN_ROOM"));
        request(a,"/api/v1/rooms/"+id(other)+"/leave",null).andExpect(status().isForbidden());
        request(a,"/api/v1/rooms/"+id(r)+"/leave",null).andExpect(status().isOk());
        request(a,"/api/v1/rooms/"+id(r)+"/leave",null).andExpect(status().isOk());
        request(b,"/api/v1/rooms/"+id(other)+"/leave",null).andExpect(status().isOk());
        request(b,"/api/v1/rooms/"+id(r)+"/join",null).andExpect(status().isNotFound());
    }
    @Test void concurrentJoinsNeverExceedCapacity()throws Exception{
        var creator=driver(true);var r=create(creator,"경쟁",true,3);var drivers=new ArrayList<Driver>();
        for(int i=0;i<8;i++)drivers.add(driver(true));
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Integer>>();
            for(var d:drivers)futures.add(pool.submit(()->request(d,"/api/v1/rooms/"+id(r)+"/join",null).andReturn().getResponse().getStatus()));
            var results=new ArrayList<Integer>();for(var f:futures)results.add(f.get(20,TimeUnit.SECONDS));
            assertThat(results).containsOnly(200,409);assertThat(Collections.frequency(results,200)).isEqualTo(2);
        }
        assertThat(getAs(creator,"/api/v1/rooms/me").path("participants").asInt()).isEqualTo(3);
    }
    @Test void sameUserConcurrentJoinCreatesOneMembership()throws Exception{
        var a=driver(true);var b=driver(true);var r=create(a,"중복",true,3);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<6;i++)tasks.add(pool.submit(()->request(b,"/api/v1/rooms/"+id(r)+"/join",null).andReturn().getResponse().getStatus()));
            for(var task:tasks)assertThat(task.get(20,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(getAs(a,"/api/v1/rooms/me").path("participants").asInt()).isEqualTo(2);
    }
    @Test void searchUsesFinalDestinationAndFiveOrTenKmAndHidesPrivateAndFullRooms()throws Exception{
        var origin=driver(true,33.0,126.0);var near=driver(true,33.03,126.0);var far=driver(true,33.07,126.0);
        var nearRoom=create(near,"가까운 방",true,2);var farRoom=create(far,"먼 방",true,2);
        var privateRoom=create(driver(true,33.01,126.0),"비공개",false,2);
        String five=getAs(origin,"/api/v1/rooms/nearby?radiusKm=5").toString();
        assertThat(five).contains(id(nearRoom)).doesNotContain(id(farRoom),id(privateRoom),"latitude","longitude");
        assertThat(getAs(origin,"/api/v1/rooms/nearby?radiusKm=10").toString()).contains(id(farRoom));
        request(origin,"/api/v1/rooms/"+id(nearRoom)+"/join",null).andExpect(status().isOk());
        assertThat(getAs(origin,"/api/v1/rooms/nearby?radiusKm=5").toString()).doesNotContain(id(nearRoom));
        mvc.perform(get("/api/v1/rooms/nearby?radiusKm=7").header("Authorization","Bearer "+origin.token())).andExpect(status().isBadRequest());
    }
    @Test void centerRemainsFixedWhenCreatorsDestinationChanges()throws Exception{
        var a=driver(true,34.0,127.0);var r=create(a,"고정 중심",true,3);destination(a,36,127);
        var b=driver(true,34,127);
        assertThat(getAs(b,"/api/v1/rooms/nearby").toString()).contains(id(r));
    }
    @Test void endingTripLeavesRoomAndNextRegistrationCreatesNewTrip()throws Exception{
        var a=driver(true);var before=getAs(a,"/api/v1/trips/me");var r=create(a,"종료",true,2);
        request(a,"/api/v1/trips/me/end",null).andExpect(status().isOk());
        request(a,"/api/v1/trips/me/end",null).andExpect(status().isOk());
        assertThat(getAs(a,"/api/v1/rooms/me").isNull()).isTrue();
        assertThat(getAs(a,"/api/v1/trips/me").isNull()).isTrue();
        request(a,"/api/v1/voice/token",Map.of("roomId",id(r))).andExpect(status().isForbidden());
        destination(a,37,127);assertThat(getAs(a,"/api/v1/trips/me").path("id")).isNotEqualTo(before.path("id"));
    }
    @Test void heartbeatRenewsLeaseAndStaleMembersExpire()throws Exception{
        var a=driver(true);var r=create(a,"연결 유지",true,2);
        request(a,"/api/v1/rooms/"+id(r)+"/heartbeat",null).andExpect(status().isOk());
        jdbc.update("update room_memberships set heartbeat_at=now()-interval '91 seconds' where user_id=?::uuid",a.user());
        voice.maintain();assertThat(getAs(a,"/api/v1/rooms/me").isNull()).isTrue();
        request(a,"/api/v1/rooms/"+id(r)+"/heartbeat",null).andExpect(status().isForbidden());
    }
    @Test void roomAndTripExpiryAreEnforcedWithoutWaitingForScheduler()throws Exception{
        var a=driver(true);var r=create(a,"만료",true,2);
        jdbc.update("update rooms set expires_at=now()-interval '1 second' where id=?::uuid",id(r));
        assertThat(getAs(a,"/api/v1/rooms/me").isNull()).isTrue();
        jdbc.update("update trip_sessions set started_at=now()-interval '26 hours',updated_at=now()-interval '25 hours' where user_id=?::uuid",a.user());
        assertThat(getAs(a,"/api/v1/trips/me").isNull()).isTrue();
    }
    @Test void tokenRequiresMembershipAndUsesStableSessionScopedIdentity()throws Exception{
        when(gateway.issue(any(),anyInt(),any(),any())).thenReturn(new VoiceGateway.Grant("ws://localhost:7880","test-token",Instant.now().plusSeconds(60)));
        var a=driver(true);var b=driver(true);var r=create(a,"권한",true,3);
        request(b,"/api/v1/voice/token",Map.of("roomId",id(r))).andExpect(status().isForbidden());
        for(int i=0;i<2;i++)request(a,"/api/v1/voice/token",Map.of("roomId",id(r))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        assertThat(jdbc.queryForObject("select count(*) from voice_leases l join room_memberships m on m.id=l.membership_id where m.user_id=?::uuid",Integer.class,a.user())).isEqualTo(1);
        request(a,"/api/v1/rooms/"+id(r)+"/leave",null).andExpect(status().isOk());
        request(a,"/api/v1/voice/token",Map.of("roomId",id(r))).andExpect(status().isForbidden());
    }
    @Test void livekitOutageDoesNotRollbackExpiry()throws Exception{
        var a=driver(true);create(a,"장애 정리",true,2);
        jdbc.update("update room_memberships set heartbeat_at=now()-interval '91 seconds' where user_id=?::uuid",a.user());
        doThrow(new RoomFailure("VOICE_UNAVAILABLE")).when(gateway).reconcile(any());
        voice.maintain();assertThat(jdbc.queryForObject("select count(*) from room_memberships where user_id=?::uuid and left_at is null",Integer.class,a.user())).isZero();
    }
    @Test void validatesRoomFieldsAndRejectsInjectedIdentity()throws Exception{
        var a=driver(true);
        for(var body:List.of(Map.of("name"," ","isPublic",true,"capacity",3),Map.of("name","방","isPublic",true,"capacity",9),
            Map.of("name","방","isPublic",true,"capacity",3,"userId",a.user())))
            request(a,"/api/v1/rooms",body).andExpect(status().isBadRequest());
    }
}
