package com.example.posts_service.exception;

import com.example.posts_service.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
        log.info("Post not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedPostAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedPostAccess(UnauthorizedPostAccessException ex) {
        log.warn("Unauthorized post access attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Login failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "Unauthorized", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPostStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPostStatus(InvalidPostStatusException ex) {
        log.warn("Invalid post status operation: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(InvalidFileException ex) {
        log.warn("Invalid file: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Bad Request", "File size exceeds maximum allowed size of 5 MB"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid path variable type: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Bad Request", "Invalid post ID format"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", message));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex) {
        log.warn("Invalid JSON in request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Invalid request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
