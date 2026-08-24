package com.example.posts_service.scheduler;

import com.example.posts_service.service.AttachmentRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttachmentRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRetryScheduler.class);

    private final AttachmentRetryService attachmentRetryService;

    public AttachmentRetryScheduler(AttachmentRetryService attachmentRetryService) {
        this.attachmentRetryService = attachmentRetryService;
    }

    @Scheduled(fixedDelayString = "${app.attachment.retry-interval-ms:30000}")
    public void processPendingUploads() {
        log.debug("Running attachment retry job");
        attachmentRetryService.processPendingUploads();
    }
}
