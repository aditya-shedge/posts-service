package com.example.posts_service.repository;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findAllByOrderByCreatedAtDesc();

    List<Post> findAllByStatusOrderByCreatedAtDesc(PostStatus status);

    List<Post> findAllByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    List<Post> findAllByCreatedByAndStatusNotOrderByCreatedAtDesc(UUID createdBy, PostStatus status);

    Optional<Post> findByIdAndStatus(UUID id, PostStatus status);

    Optional<Post> findByIdAndStatusNot(UUID id, PostStatus status);

    Page<Post> findAllByStatus(PostStatus status, Pageable pageable);

    Page<Post> findAllByCreatedByAndStatusNot(UUID createdBy, PostStatus status, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.id = :postId
            AND p.status != 'DELETED'
            AND (p.createdBy = :userId OR p.status = 'PUBLISHED' OR :isModerator = true)
            """)
    Optional<Post> findVisiblePost(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId,
            @Param("isModerator") boolean isModerator);
}
