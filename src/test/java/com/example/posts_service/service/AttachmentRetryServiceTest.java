package com.example.posts_service.service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.PostAttachment;
import com.example.posts_service.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentRetryServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @TempDir
    Path tempDir;

    private AttachmentRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new AttachmentRetryService(attachmentRepository, cloudinaryService);
    }

    @Test
    void successfulRetryUpdatesStatusToUploadedAndDeletesTempFile() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = pendingAttachment(tempFile.toString());

        when(cloudinaryService.upload(any()))
                .thenReturn(new CloudinaryUploadResult("https://cloudinary.com/test.jpg", "posts/abc123"));
        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(AttachmentStatus.UPLOADED, attachment.getStatus());
        assertEquals("https://cloudinary.com/test.jpg", attachment.getUrl());
        assertEquals("posts/abc123", attachment.getPublicId());
        assertNull(attachment.getTempPath());
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void failedRetryBelowMaxIncrementsRetryCountAndSetsNextRetryAt() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = pendingAttachment(tempFile.toString());
        attachment.setRetryCount(0);

        when(cloudinaryService.upload(any())).thenThrow(new IOException("Service unavailable"));
        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(AttachmentStatus.PENDING, attachment.getStatus());
        assertEquals(1, attachment.getRetryCount());
        assertEquals(LocalDateTime.now().plusMinutes(1).getMinute(), attachment.getNextRetryAt().getMinute());
    }

    @Test
    void secondFailedRetrySetsBackoffToFiveMinutes() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = pendingAttachment(tempFile.toString());
        attachment.setRetryCount(1);

        when(cloudinaryService.upload(any())).thenThrow(new IOException("Service unavailable"));
        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(2, attachment.getRetryCount());
        assertEquals(LocalDateTime.now().plusMinutes(5).getMinute(), attachment.getNextRetryAt().getMinute());
    }

    @Test
    void failedRetryAtMaxSetsStatusToFailedAndDeletesTempFile() throws IOException {
        Path tempFile = tempDir.resolve("test.jpg");
        Files.write(tempFile, "image content".getBytes());

        PostAttachment attachment = pendingAttachment(tempFile.toString());
        attachment.setRetryCount(2); // at max (MAX_RETRY_COUNT - 1)

        when(cloudinaryService.upload(any())).thenThrow(new IOException("Service unavailable"));
        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        assertNull(attachment.getTempPath());
        assertNull(attachment.getNextRetryAt());
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void missingTempFileMarksAttachmentAsFailedWithoutCallingCloudinary() throws IOException {
        PostAttachment attachment = pendingAttachment("/nonexistent/path/file.jpg");

        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        verify(cloudinaryService, never()).upload(any());
    }

    @Test
    void nullTempPathMarksAttachmentAsFailed() throws IOException {
        PostAttachment attachment = pendingAttachment(null);

        when(attachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retryService.retryUpload(attachment);

        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        verify(cloudinaryService, never()).upload(any());
    }

    private PostAttachment pendingAttachment(String tempPath) {
        PostAttachment attachment = new PostAttachment(UUID.randomUUID(), UUID.randomUUID());
        attachment.setStatus(AttachmentStatus.PENDING);
        attachment.setFilename("test.jpg");
        attachment.setRetryCount(0);
        attachment.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        attachment.setTempPath(tempPath);
        return attachment;
    }
}
