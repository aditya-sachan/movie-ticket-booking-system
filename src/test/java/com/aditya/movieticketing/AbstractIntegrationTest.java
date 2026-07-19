package com.aditya.movieticketing;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: boots the full application against a real PostgreSQL 16 in
 * Testcontainers (H2 will NOT reproduce SELECT ... FOR UPDATE row locking, which the concurrency
 * test depends on). Flyway migrates the schema and the UserSeeder creates the demo users.
 *
 * <p>Uses the singleton-container pattern: one container is started in a static initializer and
 * lives for the whole JVM, shared across every test class (and the cached Spring context). This
 * avoids the per-class start/stop of {@code @Testcontainers}, which would leave a later class
 * pointing at a stopped container. The Hikari pool is sized above the concurrency test's thread
 * count so 50 simultaneous transactions (49 of them blocked on the same row lock) do not exhaust
 * the pool.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The concurrency test runs 50 threads that each hold a connection while contending on the
        // same row lock; the pool must exceed that to avoid connection-acquisition timeouts.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "60");
    }
}
