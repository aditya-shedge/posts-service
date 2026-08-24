package com.example.posts_service.repository;

import com.example.posts_service.BaseIntegrationTest;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.PostAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AttachmentRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @AfterEach
    void cleanUp() {
        attachmentRepository.deleteAll();
    }

    @Test
    void findByPostIdReturnsAttachmentWhenExists() {
        UUID postId = UUID.randomUUID();
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), postId);
        attachment.setStatus(AttachmentStatus.UPLOADED);
        attachment.setUrl("https://cloudinary.com/test.jpg");
        attachmentRepository.save(attachment);

        Optional<PostAttachment> result = attachmentRepository.findByPostId(postId);

        assertTrue(result.isPresent());
        assertEquals(postId, result.get().getPostId());
        assertEquals("https://cloudinary.com/test.jpg", result.get().getUrl());
    }

    @Test
    void findByPostIdReturnsEmptyWhenNotExists() {
        Optional<PostAttachment> result = attachmentRepository.findByPostId(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    @Test
    void findPendingUploadsReadyForRetryReturnsPastDueAttachments() {
        UUID postId = UUID.randomUUID();
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), postId);
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(5));
        attachmentRepository.save(attachment);

        List<PostAttachment> results = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());

        assertEquals(1, results.size());
        assertEquals(postId, results.get(0).getPostId());
    }

    @Test
    void findPendingUploadsReadyForRetryDoesNotReturnFutureScheduledAttachments() {
        UUID postId = UUID.randomUUID();
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), postId);
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        attachmentRepository.save(attachment);

        List<PostAttachment> results = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());

        assertTrue(results.isEmpty());
    }

    @Test
    void findPendingUploadsReadyForRetryDoesNotReturnUploadedAttachments() {
        UUID postId = UUID.randomUUID();
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), postId);
        attachment.setStatus(AttachmentStatus.UPLOADED);
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(5));
        attachmentRepository.save(attachment);

        List<PostAttachment> results = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());

        assertTrue(results.isEmpty());
    }

    @Test
    void findPendingUploadsReadyForRetryReturnsEmptyWhenNonePending() {
        List<PostAttachment> results = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());

        assertTrue(results.isEmpty());
    }
}
