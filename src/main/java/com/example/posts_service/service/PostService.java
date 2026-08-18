package com.example.posts_service.service;

import com.example.posts_service.dto.CreatePostRequest;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public PostResponse createPost(CreatePostRequest request, UUID userId) {
        Post post = new Post(
                UUID.randomUUID(),
                request.getText(),
                request.getAttachment(),
                request.getRemarks(),
                PostStatus.PUBLISHED,
                userId,
                null,
                null
        );

        Post saved = postRepository.save(post);
        log.info("Created post: id={}, createdBy={}", saved.getId(), userId);

        return toResponse(saved);
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getText(),
                post.getAttachment(),
                post.getRemarks(),
                post.getStatus(),
                post.getCreatedBy(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
