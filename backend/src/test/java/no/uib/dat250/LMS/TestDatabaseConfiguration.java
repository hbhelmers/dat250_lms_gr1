package no.uib.dat250.LMS;

import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Locally the tests start PostgreSQL with Testcontainers. The GitLab runner cannot start containers, so CI
 * provides a PostgreSQL service instead (LMS_TEST_POSTGRES_URL). Each Spring test context then gets its own
 * schema, which keeps contexts isolated in the same way as separate containers do.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestDatabaseConfiguration {
    static final String EXTERNAL_URL = System.getenv("LMS_TEST_POSTGRES_URL");

    @Bean
    @ServiceConnection
    @Conditional(UseContainer.class)
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @Conditional(UseExternal.class)
    DynamicPropertyRegistrar externalPostgres() {
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        String user = System.getenv().getOrDefault("LMS_TEST_POSTGRES_USER", "lmsuser");
        return registry -> {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL + "?currentSchema=" + schema);
            registry.add("spring.datasource.username", () -> user);
            registry.add("spring.flyway.default-schema", () -> schema);
            registry.add("spring.flyway.schemas", () -> schema);
        };
    }

    static class UseContainer implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) { return EXTERNAL_URL == null; }
    }

    static class UseExternal implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) { return EXTERNAL_URL != null; }
    }
}
