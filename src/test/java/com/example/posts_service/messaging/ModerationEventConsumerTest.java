package com.example.posts_service.messaging;

import com.example.posts_service.dto.ModerationEvent;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.service.HuggingFaceModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationEventConsumerTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private HuggingFaceModerationService moderationService;

    private ModerationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ModerationEventConsumer(postRepository, moderationService);
    }

    @Test
    void safeContentSetsAiApprovedModerationStatus() {
        UUID postId = UUID.randomUUID();
        Post post = new Post(postId, "Test", null, null, PostStatus.DRAFT, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());
        ModerationEvent event = new ModerationEvent(postId, "Safe announcement", LocalDateTime.now());

        when(moderationService.isFlagged("Safe announcement")).thenReturn(false);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        consumer.consume(event);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals(PostStatus.DRAFT, captor.getValue().getStatus());
        assertEquals("AI_APPROVED", captor.getValue().getModerationStatus());
        assertNotNull(captor.getValue().getModeratedAt());
    }

    @Test
    void flaggedContentUpdatesPostStatusToRejected() {
        UUID postId = UUID.randomUUID();
        Post post = new Post(postId, "Test", null, null, PostStatus.DRAFT, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());
        ModerationEvent event = new ModerationEvent(postId, "Harmful content", LocalDateTime.now());

        when(moderationService.isFlagged("Harmful content")).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        consumer.consume(event);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals(PostStatus.REJECTED, captor.getValue().getStatus());
        assertEquals("AI_REJECTED", captor.getValue().getModerationStatus());
        assertNotNull(captor.getValue().getModerationReason());
        assertNotNull(captor.getValue().getModeratedAt());
    }

    @Test
    void openAIExceptionPropagatesForKafkaRetry() {
        UUID postId = UUID.randomUUID();
        ModerationEvent event = new ModerationEvent(postId, "Some text", LocalDateTime.now());

        when(moderationService.isFlagged(any()))
                .thenThrow(new org.springframework.web.client.RestClientException("API down"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.client.RestClientException.class,
                () -> consumer.consume(event));

        verify(postRepository, never()).save(any());
    }

    @Test
    void postNotFoundIsHandledGracefully() {
        UUID postId = UUID.randomUUID();
        ModerationEvent event = new ModerationEvent(postId, "Harmful content", LocalDateTime.now());

        when(moderationService.isFlagged(any())).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        consumer.consume(event);

        verify(postRepository, never()).save(any());
    }

}
