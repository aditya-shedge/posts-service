package com.example.posts_service.exception;

public class InvalidPostStatusException extends RuntimeException {

    public InvalidPostStatusException(String message) {
        super(message);
    }
}
