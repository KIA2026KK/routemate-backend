package com.routemate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentConfigurationTests {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ServerProperties.class, DataSourceProperties.class})
    static class PropertiesConfiguration { }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withInitializer(application -> {
            // The developer's .env and process environment must not affect deployment contract tests.
            application.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            application.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            new ConfigDataApplicationContextInitializer().initialize(application);
        })
        .withUserConfiguration(PropertiesConfiguration.class)
        .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
        .withPropertyValues("spring.config.import=", "DB_PASSWORD=unused-test-password");

    @Test void renderPortAndEncryptedPostgresConfigurationTakeEffect() {
        context.withPropertyValues("PORT=10000", "SERVER_PORT=18080", "DB_HOST=db.internal",
            "DB_NAME=routemate_render", "DB_SSL_MODE=require").run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application.getBean(ServerProperties.class).getPort()).isEqualTo(10000);
                assertThat(application.getBean(DataSourceProperties.class).getUrl())
                    .isEqualTo("jdbc:postgresql://db.internal:5432/routemate_render?sslmode=require");
            });
    }

    @Test void localHostPortConfigurationAndServerPortFallbackRemainAvailable() {
        context.withPropertyValues("SERVER_PORT=18081", "REDIS_HOST=local-redis", "REDIS_PORT=6380",
            "REDIS_PASSWORD=local-test-password").run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application.getBean(ServerProperties.class).getPort()).isEqualTo(18081);
                assertThat(application.getBean(DataSourceProperties.class).getUrl())
                    .isEqualTo("jdbc:postgresql://localhost:5432/routemate?sslmode=prefer");
                var connection = application.getBean(LettuceConnectionFactory.class);
                assertThat(connection.getHostName()).isEqualTo("local-redis");
                assertThat(connection.getPort()).isEqualTo(6380);
                assertThat(connection.getClientConfiguration().isUseSsl()).isFalse();
            });
    }

    @Test void secureRedisUrlOverridesHostPortAndPasswordWithoutConnecting() {
        context.withPropertyValues("REDIS_HOST=ignored-host", "REDIS_PORT=9999", "REDIS_PASSWORD=ignored-password")
            .withInitializer(application -> application.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("renderEnvironment", java.util.Map.of(
                    "SPRING_DATA_REDIS_URL", "rediss://app-user:url-test-password@secure-redis:6381/3"))))
            .run(application -> {
                assertThat(application).hasNotFailed();
                var connection = application.getBean(LettuceConnectionFactory.class);
                assertThat(connection.getHostName()).isEqualTo("secure-redis");
                assertThat(connection.getPort()).isEqualTo(6381);
                assertThat(connection.getDatabase()).isEqualTo(3);
                assertThat(connection.getClientConfiguration().isUseSsl()).isTrue();
                assertThat(connection.getStandaloneConfiguration().getUsername()).isEqualTo("app-user");
                assertThat(new String(connection.getStandaloneConfiguration().getPassword().get())).isEqualTo("url-test-password");
            });
    }
}
