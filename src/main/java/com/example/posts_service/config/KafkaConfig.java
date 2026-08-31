package com.example.posts_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.example.posts_service.dto.ModerationEvent;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${app.kafka.topic.post-moderation}")
    private String topicName;

    @Bean
    public NewTopic postModerationTopic() {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler moderationErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(60_000L, 5.0);
        backOff.setMaxElapsedTime(1_200_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Moderation permanently failed after all retries: value={}, error={}",
                        record.value(), exception.getMessage(), exception),
                backOff);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.warn("Retry attempt {} for moderation, root cause: {} - {}",
                    deliveryAttempt, cause.getClass().getSimpleName(), cause.getMessage(), cause);
        });

        errorHandler.addNotRetryableExceptions(org.apache.kafka.common.errors.SerializationException.class);
        errorHandler.addNotRetryableExceptions(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);
        return errorHandler;
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
