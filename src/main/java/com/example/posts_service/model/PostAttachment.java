package com.example.posts_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "post_attachments")
@Getter
@NoArgsConstructor
public class PostAttachment {

    @Id
    @Setter
    private UUID id;

    @Column(name = "post_id", nullable = false, unique = true)
    private UUID postId;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String url;

    @Column(name = "public_id")
    @Setter
    private String publicId;

    @Column(name = "filename")
    @Setter
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private AttachmentStatus status;

    @Column(name = "temp_path", length = 500)
    @Setter
    private String tempPath;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private int retryCount;

    @Column(name = "next_retry_at")
    @Setter
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostAttachment(UUID id, UUID postId) {
        this.id = id;
        this.postId = postId;
        this.retryCount = 0;
    }

    @PrePersist
    void onPersist() {
        createdAt = LocalDateTime.now();
    }
}
