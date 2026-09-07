package com.example.posts_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Post {

    @Id
    @EqualsAndHashCode.Include
    @Setter
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Setter
    private String text;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private PostStatus status;

    @Column(name = "created_by", nullable = false)
    @Setter
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "moderation_status", length = 20)
    @Setter
    private String moderationStatus;

    @Column(name = "moderation_reason", columnDefinition = "TEXT")
    @Setter
    private String moderationReason;

    @Column(name = "moderated_at")
    @Setter
    private LocalDateTime moderatedAt;

    // Constructor for creating new posts
    public Post(String text, String remarks, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.text = text;
        this.remarks = remarks;
        this.status = PostStatus.DRAFT;
        this.createdBy = createdBy;
    }

    // Backward-compatible constructor used in existing tests
    public Post(UUID id, String text, String remarks, PostStatus status,
                UUID createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.text = text;
        this.remarks = remarks;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
