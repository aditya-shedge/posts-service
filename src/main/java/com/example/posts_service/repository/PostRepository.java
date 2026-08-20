package com.example.posts_service.repository;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByOrderByCreatedAtDesc();

    List<Post> findAllByStatusOrderByCreatedAtDesc(PostStatus status);

    List<Post> findAllByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    List<Post> findAllByCreatedByAndStatusNotOrderByCreatedAtDesc(UUID createdBy, PostStatus status);

    Optional<Post> findByIdAndStatus(UUID id, PostStatus status);
}
