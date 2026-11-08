package com.routemate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.config.import=", "DB_PASSWORD=unused", "REDIS_PASSWORD="})
@AutoConfigureMockMvc
class SessionRecoveryIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private record Account(JsonNode session, String vin, String vehicleId) { }
    private Account account() throws Exception {
        var session = json.readTree(mvc.perform(post("/api/v1/auth/device-sessions"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String vin = "KNA" + UUID.randomUUID().toString().replace("-", "").substring(0,14).toUpperCase();
        var vehicle = json.readTree(mvc.perform(post("/api/v1/vehicles/me")
            .header("Authorization", bearer(session)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("vin",vin,"source","PLEOS_SDK","vinAccessType","READ"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Account(session, vin, vehicle.get("vehicleId").asText());
    }
    private String bearer(JsonNode session) { return "Bearer " + session.get("accessToken").asText(); }
    private String key(Account a) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/recovery-keys").header("Authorization",bearer(a.session())))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString()).get("recoveryKey").asText();
    }
    private String restoreBody(String vin, String key) throws Exception {
        return json.writeValueAsString(Map.of("vin",vin,"source","PLEOS_SDK","recoveryKey",key));
    }
    private JsonNode recover(Account a, String key) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/device-sessions/recover").contentType("application/json")
            .content(restoreBody(a.vin(),key))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    private int renew(JsonNode session) throws Exception {
        return mvc.perform(post("/api/v1/auth/device-sessions/renew").header("Authorization",bearer(session)))
            .andReturn().getResponse().getStatus();
    }

    @Test void renewalExtendsCurrentCredentialAndKeepsVehicleIdentity() throws Exception {
        var a=account();
        jdbc.update("update auth_sessions set expires_at=now()+interval '1 day' where user_id=?::uuid",a.session().get("userId").asText());
        var lease=json.readTree(mvc.perform(post("/api/v1/auth/device-sessions/renew").header("Authorization",bearer(a.session())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(lease.get("vehicleId").asText()).isEqualTo(a.vehicleId());
        assertThat(lease.has("accessToken")).isFalse();
        assertThat(jdbc.queryForObject("select expires_at > now()+interval '29 days' from auth_sessions where user_id=?::uuid",Boolean.class,
            a.session().get("userId").asText())).isTrue();
        assertThat(renew(a.session())).isEqualTo(200);
    }
    @Test void recoveryRetainsVehicleAndTripButRevokesPreviousSessions() throws Exception {
        var a=account(); var key=key(a);
        var trip=json.readTree(mvc.perform(post("/api/v1/trips").header("Authorization",bearer(a.session())).contentType("application/json")
            .content("{\"destination\":{\"name\":\"서울역\",\"latitude\":37.55,\"longitude\":126.97}}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var restored=recover(a,key);
        assertThat(restored.get("userId")).isEqualTo(a.session().get("userId"));
        assertThat(restored.get("vehicleId").asText()).isEqualTo(a.vehicleId());
        assertThat(renew(a.session())).isEqualTo(401);
        assertThat(renew(restored)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select id::text from trip_sessions where vehicle_id=?::uuid",String.class,a.vehicleId()))
            .isEqualTo(trip.get("tripId").asText());
        // The backup key remains usable until expiry/replacement, including after a lost response.
        var retried=recover(a,key);
        assertThat(renew(restored)).isEqualTo(401);
        assertThat(renew(retried)).isEqualTo(200);
    }
    @Test void keysAreHashedAndReplacementInvalidatesTheOldKey() throws Exception {
        var a=account(); var first=key(a); var second=key(a);
        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("select key_hash from vehicle_recovery_keys where user_id=?::uuid",String.class,
            a.session().get("userId").asText())).hasSize(64).isNotEqualTo(second);
        mvc.perform(post("/api/v1/auth/device-sessions/recover").contentType("application/json").content(restoreBody(a.vin(),first)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
        assertThat(renew(recover(a,second))).isEqualTo(200);
    }
    @Test void wrongVinWrongKeyAndExpiredKeyHaveTheSameGenericFailure() throws Exception {
        var a=account(); var b=account(); var key=key(a);
        for(var body:new String[]{restoreBody(b.vin(),key),restoreBody(a.vin(),"z".repeat(43)),restoreBody(a.vin(),"")}) {
            mvc.perform(post("/api/v1/auth/device-sessions/recover").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
        }
        jdbc.update("update vehicle_recovery_keys set created_at=now()-interval '366 days', expires_at=now()-interval '1 second' where user_id=?::uuid",
            a.session().get("userId").asText());
        mvc.perform(post("/api/v1/auth/device-sessions/recover").contentType("application/json").content(restoreBody(a.vin(),key)))
            .andExpect(status().isUnauthorized());
        assertThat(renew(a.session())).isEqualTo(200);
    }
    @Test void expiredAndRevokedCredentialsCannotRenewOrIssueNewKeys() throws Exception {
        var a=account(); var key=key(a);
        jdbc.update("update auth_sessions set created_at=now()-interval '31 days',expires_at=now()-interval '1 second' where user_id=?::uuid",
            a.session().get("userId").asText());
        assertThat(renew(a.session())).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/recovery-keys").header("Authorization",bearer(a.session()))).andExpect(status().isUnauthorized());
        var restored=recover(a,key);
        mvc.perform(post("/api/v1/auth/recovery-keys").header("Authorization",bearer(a.session()))).andExpect(status().isUnauthorized());
        assertThat(renew(restored)).isEqualTo(200);
    }
    @Test void recoveryAndConcurrentRenewalCannotReviveOldCredential() throws Exception {
        var a=account(); var key=key(a);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var renewal=executor.submit(()->renew(a.session()));
            var recovery=executor.submit(()->recover(a,key));
            assertThat(renewal.get()).isIn(200,401);
            assertThat(renew(recovery.get())).isEqualTo(200);
        }
        assertThat(renew(a.session())).isEqualTo(401);
    }
}
