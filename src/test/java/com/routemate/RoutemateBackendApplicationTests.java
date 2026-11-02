package com.routemate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.import=", "DB_PASSWORD=unused", "REDIS_PASSWORD="})
class RoutemateBackendApplicationTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired StringRedisTemplate redis;
	@LocalServerPort int port;

	@Test
	void contextLoads() throws Exception {
		assertThat(jdbc.queryForObject("SELECT PostGIS_Version()", String.class)).isNotBlank();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isPositive();
		try (var connection = redis.getConnectionFactory().getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}
		try (var client = HttpClient.newHttpClient()) {
			var response = client.send(HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("\"status\":\"UP\"");
		}
	}

}
