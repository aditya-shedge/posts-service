package com.example.posts_service;

import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CreatePostAttachmentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        postRepository.deleteAll();
    }

    @Test
    void createPostWithTextOnlyReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Field trip announcement")
                        .param("remarks", "Please sign permission slips")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.text").value("Field trip announcement"))
                .andExpect(jsonPath("$.remarks").value("Please sign permission slips"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andExpect(jsonPath("$.attachmentStatus").doesNotExist());
    }

    @Test
    void createPostWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Announcement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPostWithMissingTextReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPostWithBlankTextReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPostWithInvalidFileTypeReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        MockMultipartFile file = new MockMultipartFile(
                "attachment", "malware.exe", "application/x-msdownload", "content".getBytes());

        mockMvc.perform(multipart("/api/posts")
                        .file(file)
                        .param("text", "Announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF"));
    }

    @Test
    void createPostWithOversizedFileReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile(
                "attachment", "large.jpg", "image/jpeg", largeContent);

        mockMvc.perform(multipart("/api/posts")
                        .file(file)
                        .param("text", "Announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File size exceeds maximum allowed size of 5 MB"));
    }

    @Test
    void createPostWithRemarksOver1000CharsReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");
        String longRemarks = "a".repeat(1001);

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Announcement")
                        .param("remarks", longRemarks)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachmentFilenameIsPreservedInResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        MockMultipartFile file = new MockMultipartFile(
                "attachment", "permission-slip.pdf", "application/pdf", "pdf content".getBytes());

        com.example.posts_service.dto.CloudinaryUploadResult mockResult =
                new com.example.posts_service.dto.CloudinaryUploadResult(
                        "https://res.cloudinary.com/test/posts/abc123.pdf", "posts/abc123");

        org.mockito.Mockito.when(cloudinaryService.upload(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockResult);

        mockMvc.perform(multipart("/api/posts")
                        .file(file)
                        .param("text", "Field trip info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentFilename").value("permission-slip.pdf"))
                .andExpect(jsonPath("$.attachment").value("https://res.cloudinary.com/test/posts/abc123.pdf"))
                .andExpect(jsonPath("$.attachmentStatus").value("UPLOADED"));
    }
}
