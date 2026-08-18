package com.example.posts_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePostRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void blankTextFailsValidation() {
        CreatePostRequest request = new CreatePostRequest("  ", null, null);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("text")));
    }

    @Test
    void invalidAttachmentUrlFailsValidation() {
        CreatePostRequest request = new CreatePostRequest("Valid text", "not-a-url", null);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("attachment")));
    }

    @Test
    void remarksThatExceedMaxLengthFailsValidation() {
        String longRemarks = "a".repeat(1001);
        CreatePostRequest request = new CreatePostRequest("Valid text", null, longRemarks);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("remarks")));
    }

    @Test
    void validRequestWithAllFieldsPassesValidation() {
        CreatePostRequest request = new CreatePostRequest(
                "Field trip announcement",
                "https://school-docs.example.com/doc.pdf",
                "Contact teacher for queries"
        );

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validRequestWithNullAttachmentAndRemarksPassesValidation() {
        CreatePostRequest request = new CreatePostRequest("Simple announcement", null, null);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }
}
