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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.config.import=", "DB_PASSWORD=unused", "REDIS_PASSWORD="})
@AutoConfigureMockMvc
class VehicleIdentityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private JsonNode session() throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/anonymous"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String vin() { return "KNA" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase(); }
    private String profile(String vin) throws Exception {
        return json.writeValueAsString(java.util.Map.of("vin", vin, "source", "PLEOS_SDK", "vinAccessType", "READ"));
    }
    private JsonNode bind(JsonNode session, String vin) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/vehicles/me")
            .header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(profile(vin)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("ownershipVerified").value(false)).andReturn().getResponse().getContentAsString());
    }

    @Test void canonicalVinIsUniqueAndRawVinIsNotReturned() throws Exception {
        var session = session(); var vin = vin();
        var first = bind(session, " " + vin.toLowerCase(java.util.Locale.ROOT) + " ");
        var retry = bind(session, vin);
        assertThat(retry.get("vehicleId")).isEqualTo(first.get("vehicleId"));
        assertThat(first.get("maskedVin").asText()).isEqualTo("*************" + vin.substring(13));
        assertThat(first.toString()).doesNotContain(vin);
        assertThat(jdbc.queryForObject("select vin from vehicles where user_id = ?::uuid", String.class,
            session.get("userId").asText())).isEqualTo(vin);
        assertThat(jdbc.queryForObject("select vin_access_type from vehicles where user_id = ?::uuid", String.class,
            session.get("userId").asText())).isEqualTo("READ");
    }

    @Test void vinAloneCannotIssueOrTakeOverAnotherUsersSession() throws Exception {
        var first = session(); var second = session(); var vin = vin();
        var vehicle = bind(first, vin);
        mvc.perform(post("/api/v1/vehicles/me").contentType("application/json").content(profile(vin)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/vehicles/me").header("Authorization", "Bearer " + second.get("accessToken").asText())
            .contentType("application/json").content(profile(vin)))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("VEHICLE_CONFLICT"));
        assertThat(bind(first, vin).get("vehicleId")).isEqualTo(vehicle.get("vehicleId"));
        assertThat(jdbc.queryForObject("select count(*) from vehicles where user_id = ?::uuid", Integer.class,
            second.get("userId").asText())).isZero();
    }

    @Test void existingAccountCannotSilentlySwitchToADifferentVin() throws Exception {
        var session = session(); bind(session, vin());
        mvc.perform(post("/api/v1/vehicles/me").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(profile(vin())))
            .andExpect(status().isConflict());
    }

    @Test void tripRequiresLinkedVehicleAndUsesServerResolvedVehicleId() throws Exception {
        var session = session();
        var body = "{\"destination\":{\"name\":\"서울역\",\"latitude\":37.55,\"longitude\":126.97}}";
        mvc.perform(post("/api/v1/trips").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(body))
            .andExpect(status().is(428)).andExpect(jsonPath("code").value("VEHICLE_REQUIRED"));
        var vehicle = bind(session, vin());
        var trip = json.readTree(mvc.perform(post("/api/v1/trips")
            .header("Authorization", "Bearer " + session.get("accessToken").asText()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(trip.get("vehicleId")).isEqualTo(vehicle.get("vehicleId"));
        assertThat(jdbc.queryForObject("select vehicle_id::text from trip_sessions where id = ?::uuid", String.class,
            trip.get("tripId").asText())).isEqualTo(vehicle.get("vehicleId").asText());
    }

    @ParameterizedTest @ValueSource(strings = {"", "SHORT", "00000000000000000", "KNA0000000000000I", "KNA0000000000000O", "KNA0000000000000Q", "한글000000000000000"})
    void invalidVinsAreRejected(String vin) throws Exception {
        var session = session();
        mvc.perform(post("/api/v1/vehicles/me").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content(profile(vin)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
    }

    @Test void unsupportedAccessAndMockSourceAreRejectedByDefault() throws Exception {
        var session = session();
        for (var access : new String[] {"NONE", "WRITE"}) {
            mvc.perform(post("/api/v1/vehicles/me").header("Authorization", "Bearer " + session.get("accessToken").asText())
                .contentType("application/json").content(profile(vin()).replace("\"READ\"", "\"" + access + "\"")))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/vehicles/me").header("Authorization", "Bearer " + session.get("accessToken").asText())
            .contentType("application/json").content("{\"vin\":\""+vin()+"\",\"source\":\"MOCK\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("MOCK_VEHICLE_DISABLED"));
    }

    @Test void concurrentBindingReturnsOneVehicle() throws Exception {
        var session = session(); var vin = vin();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 6).mapToObj(i -> executor.submit(() -> bind(session, vin))).toList();
            var id = futures.getFirst().get().get("vehicleId");
            for (var future : futures) assertThat(future.get().get("vehicleId")).isEqualTo(id);
        }
        assertThat(jdbc.queryForObject("select count(*) from vehicles where user_id = ?::uuid", Integer.class,
            session.get("userId").asText())).isEqualTo(1);
    }

    @Test void mockNamespaceCannotReserveARealVinAndDisabledMockCannotRegisterTrips() throws Exception {
        var mock = session(); var real = session(); var vin = vin(); var mockVehicle = UUID.randomUUID();
        // Simulate development data remaining after mock support has been disabled.
        jdbc.update("insert into vehicles(id,user_id,vin,source,created_at,updated_at) values (?,?::uuid,?,'MOCK',now(),now())",
            mockVehicle, mock.get("userId").asText(), vin);
        assertThat(bind(real, vin).get("vehicleId").asText()).isNotEqualTo(mockVehicle.toString());
        mvc.perform(post("/api/v1/trips").header("Authorization", "Bearer " + mock.get("accessToken").asText())
            .contentType("application/json").content("{\"destination\":{\"name\":\"서울역\",\"latitude\":37.55,\"longitude\":126.97}}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("MOCK_VEHICLE_DISABLED"));
    }
}
