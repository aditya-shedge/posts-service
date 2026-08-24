package com.example.posts_service.repository;

import com.example.posts_service.model.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<PostAttachment, UUID> {

    Optional<PostAttachment> findByPostId(UUID postId);

    @Query("SELECT a FROM PostAttachment a WHERE a.status = 'PENDING' AND a.nextRetryAt <= :now")
    List<PostAttachment> findPendingUploadsReadyForRetry(@Param("now") LocalDateTime now);
}
