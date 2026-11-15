package com.example.mtiso.audit;

import com.example.mtiso.api.ConversionResponse;
import com.example.mtiso.mapper.ConversionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying schema creation via Flyway, JPA persistence,
 * and retrieval against a real PostgreSQL 16 instance managed by Testcontainers.
 *
 * Configured with @Testcontainers(disabledWithoutDocker = true) and
 * @EnabledIfSystemProperty(named = "docker.enabled", matches = "true") with lazy
 * container instantiation, ensuring it runs seamlessly in CI / containerized environments
 * when Docker is active while allowing fast offline sandboxed test execution.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "docker.enabled", matches = "true")
@SpringBootTest
class PostgresTestcontainersIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    private static synchronized PostgreSQLContainer<?> getPostgres() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mtiso_tc")
                    .withUsername("mtiso_tc_user")
                    .withPassword("mtiso_tc_pass");
            postgres.start();
        }
        return postgres;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> container = getPostgres();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired
    private ConversionService conversionService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ConversionRepository conversionRepository;

    private String loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + path, e);
        }
    }

    @Test
    @DisplayName("Testcontainers PostgreSQL: Run Flyway migration, convert MT103, persist audit record and approval")
    void testFullPostgresIntegration() {
        String rawMt103 = loadResource("/samples/mt103/01_minimal_valid.txt");

        ConversionResponse response = conversionService.convert(rawMt103);
        UUID conversionId = response.id();

        assertThat(conversionId).isNotNull();

        Optional<ConversionEntity> saved = conversionRepository.findById(conversionId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.get().getInputHash()).isEqualTo(AuditService.computeSha256(rawMt103));

        ApprovalDto approval = auditService.recordApproval(conversionId, "APPROVED", "tc_auditor", "PostgreSQL container verified");
        assertThat(approval).isNotNull();
        assertThat(approval.actor()).isEqualTo("tc_auditor");
    }
}
