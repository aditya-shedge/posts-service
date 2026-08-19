package com.example.posts_service.exception;

import java.util.UUID;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(UUID postId) {
        super(String.format("Post not found with id: %s", postId));
    }
}
