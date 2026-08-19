package com.example.posts_service.exception;

import java.util.UUID;

public class UnauthorizedPostAccessException extends RuntimeException {

    public UnauthorizedPostAccessException(UUID postId) {
        super("You can only modify your own posts");
    }
}
