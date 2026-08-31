package com.example.posts_service.messaging;

import com.example.posts_service.dto.ModerationEvent;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.service.HuggingFaceModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ModerationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventConsumer.class);

    private final PostRepository postRepository;
    private final HuggingFaceModerationService moderationService;

    public ModerationEventConsumer(PostRepository postRepository,
                                   HuggingFaceModerationService moderationService) {
        this.postRepository = postRepository;
        this.moderationService = moderationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.post-moderation}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ModerationEvent event) {
        if (event == null) {
            log.error("Received null moderation event — likely a deserialization failure");
            return;
        }
        log.info("Received moderation event: postId={}", event.getPostId());

        boolean flagged = moderationService.isFlagged(event.getText());

        if (flagged) {
            postRepository.findById(event.getPostId()).ifPresentOrElse(post -> {
                post.setStatus(PostStatus.REJECTED);
                post.setModerationStatus("AI_REJECTED");
                post.setModerationReason("Content flagged by OpenAI Moderation API");
                post.setModeratedAt(LocalDateTime.now());
                postRepository.save(post);
                log.info("Post rejected by AI moderation: postId={}", event.getPostId());
            }, () -> log.warn("Post not found for moderation: postId={}", event.getPostId()));
        } else {
            postRepository.findById(event.getPostId()).ifPresentOrElse(post -> {
                post.setModerationStatus("AI_APPROVED");
                post.setModeratedAt(LocalDateTime.now());
                postRepository.save(post);
                log.info("Post approved by AI moderation, available for human review: postId={}", event.getPostId());
            }, () -> log.warn("Post not found for moderation: postId={}", event.getPostId()));
        }
    }
}
