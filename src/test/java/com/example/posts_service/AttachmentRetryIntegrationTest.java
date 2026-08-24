package com.example.posts_service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.PostAttachment;
import com.example.posts_service.repository.AttachmentRepository;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.service.AttachmentRetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@AutoConfigureMockMvc
class AttachmentRetryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AttachmentRetryService retryService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private PostRepository postRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        attachmentRepository.deleteAll();
    }

    @Test
    void pendingAttachmentWithPastRetryAtIsPickedUpAndUpdatedToUploaded() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), UUID.randomUUID());
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setFilename("test.jpg");
        attachment.setRetryCount(0);
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(5));
        attachment.setTempPath(tempFile.toString());
        attachmentRepository.save(attachment);

        when(cloudinaryService.upload(any()))
                .thenReturn(new CloudinaryUploadResult("https://cloudinary.com/test.jpg", "posts/abc123"));

        retryService.processPendingUploads();

        PostAttachment updated = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertEquals(AttachmentStatus.UPLOADED, updated.getStatus());
        assertEquals("https://cloudinary.com/test.jpg", updated.getUrl());
        assertEquals("posts/abc123", updated.getPublicId());
        assertNull(updated.getTempPath());
    }

    @Test
    void pendingAttachmentWithFutureRetryAtIsNotPickedUp() {
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), UUID.randomUUID());
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setFilename("test.jpg");
        attachment.setRetryCount(0);
        attachment.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        attachment.setTempPath("/some/path/test.jpg");
        attachmentRepository.save(attachment);

        retryService.processPendingUploads();

        PostAttachment unchanged = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertEquals(AttachmentStatus.PENDING, unchanged.getStatus());
    }

    @Test
    void pendingAttachmentReachingMaxRetriesIsMarkedFailed() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), UUID.randomUUID());
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setFilename("test.jpg");
        attachment.setRetryCount(2); // at max
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        attachment.setTempPath(tempFile.toString());
        attachmentRepository.save(attachment);

        when(cloudinaryService.upload(any())).thenThrow(new IOException("Service unavailable"));

        retryService.processPendingUploads();

        PostAttachment updated = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertEquals(AttachmentStatus.FAILED, updated.getStatus());
        assertNull(updated.getTempPath());
        assertNull(updated.getNextRetryAt());
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void tempFileIsDeletedAfterSuccessfulUpload() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), UUID.randomUUID());
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setFilename("test.jpg");
        attachment.setRetryCount(0);
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        attachment.setTempPath(tempFile.toString());
        attachmentRepository.save(attachment);

        when(cloudinaryService.upload(any()))
                .thenReturn(new CloudinaryUploadResult("https://cloudinary.com/test.jpg", "posts/abc123"));

        retryService.processPendingUploads();

        assertFalse(Files.exists(tempFile));
    }
}
