package com.example.posts_service.messaging;

import com.example.posts_service.dto.ModerationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ModerationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventProducer.class);

    private final KafkaTemplate<String, ModerationEvent> kafkaTemplate;

    @Value("${app.kafka.topic.post-moderation}")
    private String topicName;

    public ModerationEventProducer(KafkaTemplate<String, ModerationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UUID postId, String text) {
        ModerationEvent event = new ModerationEvent(postId, text, LocalDateTime.now());
        kafkaTemplate.send(topicName, postId.toString(), event);
        log.info("Published moderation event: postId={}", postId);
    }
}
