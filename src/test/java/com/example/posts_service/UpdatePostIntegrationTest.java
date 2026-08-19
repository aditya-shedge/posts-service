package com.example.posts_service;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UpdatePostIntegrationTest {

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
    void requestWithoutAuthorizationHeaderReturns401() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Updated text"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnPostReturns200WithUpdatedFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                "https://old.com/doc.pdf", "Old remarks", PostStatus.PUBLISHED, userId, null, null));

        mockMvc.perform(put("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text",
                                  "attachment": "https://new.com/doc.pdf",
                                  "remarks": "New remarks"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId().toString()))
                .andExpect(jsonPath("$.text").value("Updated text"))
                .andExpect(jsonPath("$.attachment").value("https://new.com/doc.pdf"))
                .andExpect(jsonPath("$.remarks").value("New remarks"));
    }

    @Test
    void updateWithNullAttachmentClearsAttachment() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                "https://old.com/doc.pdf", null, PostStatus.PUBLISHED, userId, null, null));

        mockMvc.perform(put("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text",
                                  "attachment": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment").doesNotExist());
    }

    @Test
    void updateAnotherUsersPostReturns403Forbidden() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String otherUserToken = TestJwtUtil.generateToken(otherUserId, "other.teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.PUBLISHED, ownerId, null, null));

        mockMvc.perform(put("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Attempted update"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You can only modify your own posts"));
    }

    @Test
    void updateNonExistentPostReturns404NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(put("/api/posts/{postId}", nonExistentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Updated text"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Post not found with id: " + nonExistentId));
    }

    @Test
    void updateWithInvalidUuidReturns400BadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(put("/api/posts/not-a-uuid")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Updated text"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createdAtRemainsUnchangedAfterUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.PUBLISHED, userId, null, null));
        String originalCreatedAt = post.getCreatedAt().toString();

        Thread.sleep(10);

        mockMvc.perform(put("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Updated text"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt));
    }

    @Test
    void updatedAtChangesAfterUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Original text",
                null, null, PostStatus.PUBLISHED, userId, null, null));

        Thread.sleep(10);

        mockMvc.perform(put("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Updated text"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(org.hamcrest.Matchers.not(post.getUpdatedAt().toString())));
    }
}
