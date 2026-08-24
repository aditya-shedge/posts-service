package com.example.posts_service.service;

import com.example.posts_service.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidationServiceTest {

    private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        fileValidationService = new FileValidationService();
    }

    @Test
    void validJpegFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void validPngFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "test-image.png",
                "image/png",
                "test image content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void validGifFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "test-image.gif",
                "image/gif",
                "test image content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void validPdfFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "document.pdf",
                "application/pdf",
                "test pdf content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void validDocFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "document.doc",
                "application/msword",
                "test doc content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void validDocxFilePassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "test docx content".getBytes()
        );

        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    void fileExceeding5MBThrowsInvalidFileException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "large-image.jpg",
                "image/jpeg",
                largeContent
        );

        InvalidFileException exception = assertThrows(
                InvalidFileException.class,
                () -> fileValidationService.validate(file)
        );

        assertEquals("File size exceeds maximum allowed size of 5 MB", exception.getMessage());
    }

    @Test
    void invalidFileTypeThrowsInvalidFileException() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "malware.exe",
                "application/x-msdownload",
                "malicious content".getBytes()
        );

        InvalidFileException exception = assertThrows(
                InvalidFileException.class,
                () -> fileValidationService.validate(file)
        );

        assertEquals("File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF", exception.getMessage());
    }

    @Test
    void nullContentTypeThrowsInvalidFileException() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "unknown-file",
                null,
                "some content".getBytes()
        );

        InvalidFileException exception = assertThrows(
                InvalidFileException.class,
                () -> fileValidationService.validate(file)
        );

        assertEquals("File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF", exception.getMessage());
    }

    @Test
    void emptyFileThrowsInvalidFileException() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        InvalidFileException exception = assertThrows(
                InvalidFileException.class,
                () -> fileValidationService.validate(file)
        );

        assertEquals("File is empty", exception.getMessage());
    }

    @Test
    void textFileThrowsInvalidFileException() {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "notes.txt",
                "text/plain",
                "some text content".getBytes()
        );

        InvalidFileException exception = assertThrows(
                InvalidFileException.class,
                () -> fileValidationService.validate(file)
        );

        assertEquals("File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF", exception.getMessage());
    }
}
