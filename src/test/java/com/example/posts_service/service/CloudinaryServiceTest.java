package com.example.posts_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.posts_service.dto.CloudinaryUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService(cloudinary);
    }

    @Test
    void uploadSucceedsAndReturnsUrlAndPublicId() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        Map<String, Object> uploadResult = Map.of(
                "secure_url", "https://res.cloudinary.com/test/image/upload/v123/posts/abc123.jpg",
                "public_id", "posts/abc123"
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(uploadResult);

        CloudinaryUploadResult result = cloudinaryService.upload(file);

        assertEquals("https://res.cloudinary.com/test/image/upload/v123/posts/abc123.jpg", result.getUrl());
        assertEquals("posts/abc123", result.getPublicId());
    }

    @Test
    void uploadThrowsIOExceptionOnCloudinaryFailure() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "attachment",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new IOException("Cloudinary service unavailable"));

        assertThrows(IOException.class, () -> cloudinaryService.upload(file));
    }

    @Test
    void deleteCallsCloudinaryDestroy() throws IOException {
        String publicId = "posts/abc123";

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(String.class), any(Map.class))).thenReturn(Map.of("result", "ok"));

        cloudinaryService.delete(publicId);

        verify(uploader).destroy(publicId, Map.of());
    }
}
