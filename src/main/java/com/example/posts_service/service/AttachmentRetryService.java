package com.example.posts_service.service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.PostAttachment;
import com.example.posts_service.repository.AttachmentRepository;
import com.example.posts_service.util.ByteArrayMultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttachmentRetryService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRetryService.class);
    private static final int MAX_RETRY_COUNT = 3;
    private static final int[] RETRY_DELAYS_MINUTES = {1, 5, 15};

    private final AttachmentRepository attachmentRepository;
    private final CloudinaryService cloudinaryService;

    public AttachmentRetryService(AttachmentRepository attachmentRepository,
                                  CloudinaryService cloudinaryService) {
        this.attachmentRepository = attachmentRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public void processPendingUploads() {
        List<PostAttachment> pending = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());
        log.info("Processing {} pending attachment uploads", pending.size());
        pending.forEach(this::retryUpload);
    }

    void retryUpload(PostAttachment attachment) {
        String tempPath = attachment.getTempPath();

        if (tempPath == null || !Files.exists(Paths.get(tempPath))) {
            log.warn("Temp file missing for attachment: id={}", attachment.getId());
            markAsFailed(attachment);
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(tempPath));
            String contentType = Files.probeContentType(Paths.get(tempPath));
            ByteArrayMultipartFile file = new ByteArrayMultipartFile(
                    "attachment", attachment.getFilename(), contentType, fileBytes);

            CloudinaryUploadResult result = cloudinaryService.upload(file);

            attachment.setUrl(result.getUrl());
            attachment.setPublicId(result.getPublicId());
            attachment.setStatus(AttachmentStatus.UPLOADED);
            attachment.setTempPath(null);
            attachment.setNextRetryAt(null);
            attachmentRepository.save(attachment);

            deleteTempFile(tempPath);
            log.info("Retry succeeded: attachmentId={}, postId={}", attachment.getId(), attachment.getPostId());

        } catch (IOException e) {
            int retryCount = attachment.getRetryCount();
            log.warn("Retry failed: attachmentId={}, attempt={}, error={}", attachment.getId(), retryCount + 1, e.getMessage());

            if (retryCount >= MAX_RETRY_COUNT - 1) {
                markAsFailed(attachment);
                deleteTempFile(tempPath);
            } else {
                attachment.setRetryCount(retryCount + 1);
                attachment.setNextRetryAt(LocalDateTime.now().plusMinutes(RETRY_DELAYS_MINUTES[retryCount]));
                attachmentRepository.save(attachment);
            }
        }
    }

    private void markAsFailed(PostAttachment attachment) {
        attachment.setStatus(AttachmentStatus.FAILED);
        attachment.setTempPath(null);
        attachment.setNextRetryAt(null);
        attachmentRepository.save(attachment);
        log.error("Attachment permanently failed: attachmentId={}, postId={}", attachment.getId(), attachment.getPostId());
    }

    private void deleteTempFile(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("Failed to delete temp file: path={}", path);
        }
    }
}
