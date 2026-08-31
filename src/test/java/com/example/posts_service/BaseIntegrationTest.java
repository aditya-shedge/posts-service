package com.example.posts_service;

import com.example.posts_service.dto.ModerationEvent;
import com.example.posts_service.service.CloudinaryService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    protected CloudinaryService cloudinaryService;

    // Mock KafkaTemplate so non-Kafka tests don't need a real Kafka broker
    @MockitoBean
    @SuppressWarnings("rawtypes")
    protected KafkaTemplate kafkaTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // KafkaTemplate is mocked, so point to a non-existent broker to avoid connection attempts
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
    }
}
