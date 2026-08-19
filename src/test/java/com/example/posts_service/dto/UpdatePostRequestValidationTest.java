package com.example.posts_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePostRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void blankTextFailsValidation() {
        UpdatePostRequest request = new UpdatePostRequest("  ", null, null);

        Set<ConstraintViolation<UpdatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("text")));
    }

    @Test
    void invalidAttachmentUrlFailsValidation() {
        UpdatePostRequest request = new UpdatePostRequest("Valid text", "not-a-url", null);

        Set<ConstraintViolation<UpdatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("attachment")));
    }

    @Test
    void remarksThatExceedMaxLengthFailsValidation() {
        String longRemarks = "a".repeat(1001);
        UpdatePostRequest request = new UpdatePostRequest("Valid text", null, longRemarks);

        Set<ConstraintViolation<UpdatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("remarks")));
    }

    @Test
    void validRequestWithAllFieldsPassesValidation() {
        UpdatePostRequest request = new UpdatePostRequest(
                "Updated announcement",
                "https://school-docs.example.com/doc.pdf",
                "Updated remarks"
        );

        Set<ConstraintViolation<UpdatePostRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validRequestWithNullAttachmentAndRemarksPassesValidation() {
        UpdatePostRequest request = new UpdatePostRequest("Simple update", null, null);

        Set<ConstraintViolation<UpdatePostRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }
}
