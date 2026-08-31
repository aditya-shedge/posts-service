package com.example.posts_service.config;

import com.example.posts_service.dto.ModerationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class TestKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public JsonMapper kafkaJsonMapper() {
        // Jackson 3 discovers modules automatically via ServiceLoader
        return JsonMapper.builder().findAndAddModules().build();
    }

    @Bean
    public ProducerFactory<String, ModerationEvent> producerFactory(JsonMapper kafkaJsonMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new DefaultKafkaProducerFactory<>(config,
                new StringSerializer(),
                new JacksonJsonSerializer<ModerationEvent>(kafkaJsonMapper));
    }

    @Bean
    public KafkaTemplate<String, ModerationEvent> kafkaTemplate(
            ProducerFactory<String, ModerationEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, ModerationEvent> consumerFactory(JsonMapper kafkaJsonMapper) {
        JacksonJsonDeserializer<ModerationEvent> valueDeserializer =
                new JacksonJsonDeserializer<ModerationEvent>(ModerationEvent.class, kafkaJsonMapper);
        valueDeserializer.setUseTypeHeaders(false);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "posts-service-test");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ModerationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, ModerationEvent> consumerFactory,
            DefaultErrorHandler moderationErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ModerationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(moderationErrorHandler);
        return factory;
    }
}
