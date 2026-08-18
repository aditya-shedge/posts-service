package com.example.posts_service.dto;

import com.example.posts_service.model.PostStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class PostResponse {

    private UUID id;
    private String text;
    private String attachment;
    private String remarks;
    private PostStatus status;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PostResponse() {
    }

    public PostResponse(UUID id, String text, String attachment, String remarks, PostStatus status,
                        UUID createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.text = text;
        this.attachment = attachment;
        this.remarks = remarks;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getAttachment() {
        return attachment;
    }

    public String getRemarks() {
        return remarks;
    }

    public PostStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
