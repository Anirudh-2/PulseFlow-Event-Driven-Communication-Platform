package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke integration test proving Postgres + RabbitMQ can be started via Testcontainers.
 * Full Spring context bootstrapping is intentionally kept light here so CI can run without Keycloak.
 */
@Testcontainers(disabledWithoutDocker = true)
class InfrastructureContainersTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulseflow")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @SuppressWarnings("resource")
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Test
    void containersAreReachable() {
        assertTrue(postgres.isRunning());
        assertTrue(rabbit.isRunning());
        assertTrue(postgres.getJdbcUrl().contains("jdbc:postgresql"));
        assertTrue(rabbit.getAmqpUrl().startsWith("amqp://"));
    }
}
