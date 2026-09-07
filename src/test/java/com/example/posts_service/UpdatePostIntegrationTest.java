package com.example.posts_service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostAttachment;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.AttachmentRepository;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UpdatePostIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        postRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        attachmentRepository.deleteAll();
        postRepository.deleteAll();
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder putMultipart(String url, Object... uriVars) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart(url, uriVars).with(request -> {
                    request.setMethod("PUT");
                    return request;
                });
    }

    @Test
    void requestWithoutAuthorizationHeaderReturns401() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(putMultipart("/api/posts/{postId}", postId)
                        .param("text", "Updated text"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnDraftPostReturns200WithUpdatedTextAndRemarks() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, "Old remarks", PostStatus.DRAFT, userId, null, null));

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text")
                        .param("remarks", "New remarks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId().toString()))
                .andExpect(jsonPath("$.text").value("Updated text"))
                .andExpect(jsonPath("$.remarks").value("New remarks"));
    }

    @Test
    void updateAnotherUsersDraftPostReturns403Forbidden() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String otherUserToken = TestJwtUtil.generateToken(otherUserId, "other.teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, ownerId, null, null));

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Attempted update")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You can only modify your own posts"));
    }

    @Test
    void updateNonExistentPostReturns404NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(putMultipart("/api/posts/{postId}", nonExistentId)
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Post not found with id: " + nonExistentId));
    }

    @Test
    void updateWithInvalidUuidReturns400BadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(putMultipart("/api/posts/not-a-uuid")
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateWithBlankTextReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original",
                null, null, PostStatus.DRAFT, userId, null, null));

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotUpdatePublishedPost() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Published post",
                null, null, PostStatus.PUBLISHED, userId, null, null));

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Can only edit DRAFT posts"));
    }

    @Test
    void createdAtRemainsUnchangedAfterUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));
        String originalCreatedAt = post.getCreatedAt().toString();

        Thread.sleep(10);

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt));
    }

    @Test
    void updatedAtChangesAfterUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));

        Thread.sleep(10);

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(org.hamcrest.Matchers.not(post.getUpdatedAt().toString())));
    }

    @Test
    void addingAttachmentToPostWithNoneUploadsSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));

        MockMultipartFile file = new MockMultipartFile(
                "attachment", "photo.jpg", "image/jpeg", "content".getBytes());

        when(cloudinaryService.upload(any())).thenReturn(
                new CloudinaryUploadResult("https://cloudinary.com/photo.jpg", "posts/abc123"));

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .file(file)
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment").value("https://cloudinary.com/photo.jpg"))
                .andExpect(jsonPath("$.attachmentFilename").value("photo.jpg"))
                .andExpect(jsonPath("$.attachmentStatus").value("UPLOADED"));
    }

    @Test
    void removingAttachmentClearsAttachmentFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));

        PostAttachment attachment = new PostAttachment(post.getId(), "old.jpg",
                AttachmentStatus.UPLOADED, 0);
        attachment.setPublicId("posts/old-id");
        attachment.setUrl("https://cloudinary.com/old.jpg");
        attachmentRepository.save(attachment);

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text")
                        .param("removeAttachment", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andExpect(jsonPath("$.attachmentFilename").doesNotExist())
                .andExpect(jsonPath("$.attachmentStatus").doesNotExist());

        Optional<PostAttachment> deleted = attachmentRepository.findByPostId(post.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    void updateTextOnlyLeavesAttachmentUnchanged() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));

        PostAttachment attachment = new PostAttachment(post.getId(), "file.pdf",
                AttachmentStatus.UPLOADED, 0);
        attachment.setUrl("https://cloudinary.com/file.pdf");
        attachmentRepository.save(attachment);

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .param("text", "Updated text only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Updated text only"))
                .andExpect(jsonPath("$.attachment").value("https://cloudinary.com/file.pdf"));
    }

    @Test
    void uploadingInvalidFileTypeReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.DRAFT, userId, null, null));

        MockMultipartFile file = new MockMultipartFile(
                "attachment", "script.exe", "application/x-msdownload", "content".getBytes());

        mockMvc.perform(putMultipart("/api/posts/{postId}", post.getId())
                        .file(file)
                        .param("text", "Updated text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF"));
    }
}
