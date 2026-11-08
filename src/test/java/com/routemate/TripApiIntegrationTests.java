package com.routemate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.config.import=", "DB_PASSWORD=unused", "REDIS_PASSWORD="})
@AutoConfigureMockMvc
class TripApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private JsonNode session() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/anonymous"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("accessToken").isString()).andExpect(jsonPath("userId").isString())
            .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    private String body(String name, double lat, double lon) throws Exception {
        return json.writeValueAsString(java.util.Map.of("destination", java.util.Map.of(
            "name", name, "latitude", lat, "longitude", lon)));
    }
    private JsonNode register(JsonNode session, String body) throws Exception {
        var result = mvc.perform(post("/api/v1/trips").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("ACTIVE")).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void anonymousCredentialsAreHashedAndCannotBeReplacedWithUserId() throws Exception {
        var session = session();
        var stored = jdbc.queryForObject("select token_hash from auth_sessions where user_id = ?::uuid", String.class,
            session.get("userId").asText());
        assertThat(stored).hasSize(64).isNotEqualTo(session.get("accessToken").asText());
        mvc.perform(post("/api/v1/trips").header("Authorization", session.get("userId").asText())
            .contentType("application/json").content(body("서울역", 37.55, 126.97)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
    }

    @Test void registrationAndRetryUseOneActiveTripAndPreserveCoordinateOrder() throws Exception {
        var session = session();
        var first = register(session, body(" 서울역 ", 37.5547, 126.9706));
        var retry = register(session, body("서울역", 37.5547, 126.9706));
        assertThat(retry.get("tripId")).isEqualTo(first.get("tripId"));
        assertThat(first.at("/destination/name").asText()).isEqualTo("서울역");
        assertThat(jdbc.queryForObject("select ST_X(destination::geometry) from trip_sessions where id = ?::uuid",
            Double.class, first.get("tripId").asText())).isEqualTo(126.9706);
        assertThat(jdbc.queryForObject("select ST_Y(destination::geometry) from trip_sessions where id = ?::uuid",
            Double.class, first.get("tripId").asText())).isEqualTo(37.5547);
        var changed = register(session, body("부산역", 35.115, 129.042));
        assertThat(changed.get("tripId")).isEqualTo(first.get("tripId"));
        assertThat(changed.get("startedAt")).isEqualTo(first.get("startedAt"));
        assertThat(changed.at("/destination/name").asText()).isEqualTo("부산역");
        assertThat(jdbc.queryForObject("select count(*) from trip_sessions where user_id = ?::uuid and ended_at is null",
            Integer.class, session.get("userId").asText())).isEqualTo(1);
    }

    @Test void usersHaveSeparateTripsAndExpiredCredentialsAreRejected() throws Exception {
        var a = session(); var b = session();
        var first = register(a, body("서울역", 37.55, 126.97));
        var second = register(b, body("부산역", 35.11, 129.04));
        assertThat(first.get("tripId")).isNotEqualTo(second.get("tripId"));
        jdbc.update("update auth_sessions set created_at = now() - interval '31 days', expires_at = now() - interval '1 second' where user_id = ?::uuid",
            a.get("userId").asText());
        mvc.perform(post("/api/v1/trips").header("Authorization", "Bearer " + a.get("accessToken").asText())
            .contentType("application/json").content(body("서울역", 37.55, 126.97)))
            .andExpect(status().isUnauthorized());
        assertThat(register(b, body("부산역", 35.11, 129.04)).get("tripId")).isEqualTo(second.get("tripId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"destination\":null}",
        "{\"destination\":{\"name\":\"\",\"latitude\":0,\"longitude\":0}}",
        "{\"destination\":{\"name\":\"x\",\"latitude\":91,\"longitude\":0}}",
        "{\"destination\":{\"name\":\"x\",\"latitude\":0,\"longitude\":181}}",
        "{\"destination\":{\"name\":\"x\",\"longitude\":0}}",
        "{\"destination\":{\"name\":\"x\",\"latitude\":\"NaN\",\"longitude\":0}}",
        "{\"userId\":\"forged\",\"destination\":{\"name\":\"x\",\"latitude\":0,\"longitude\":0}}"
    })
    void invalidRequestsDoNotCreateTrips(String body) throws Exception {
        var session = session();
        mvc.perform(post("/api/v1/trips").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("select count(*) from trip_sessions where user_id = ?::uuid", Integer.class,
            session.get("userId").asText())).isZero();
    }

    @Test void missingAndUnknownTokensAreUnauthorized() throws Exception {
        for (var token : new String[] {"", "Bearer " + "a".repeat(43)}) {
            mvc.perform(post("/api/v1/trips").header("Authorization", token).contentType("application/json")
                .content(body("서울역", 37.55, 126.97)))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        }
    }

    @Test void concurrentRegistrationCannotCreateDuplicateActiveTrips() throws Exception {
        var session = session();
        var request = body("서울역", 37.55, 126.97);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 6).mapToObj(i -> executor.submit(() -> register(session, request))).toList();
            var id = futures.getFirst().get().get("tripId");
            for (var future : futures) assertThat(future.get().get("tripId")).isEqualTo(id);
        }
        assertThat(jdbc.queryForObject("select count(*) from trip_sessions where user_id = ?::uuid", Integer.class,
            session.get("userId").asText())).isEqualTo(1);
    }
}
