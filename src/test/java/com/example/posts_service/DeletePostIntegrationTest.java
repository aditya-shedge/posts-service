package com.example.posts_service;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DeletePostIntegrationTest extends BaseIntegrationTest {

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

        mockMvc.perform(delete("/api/posts/{postId}", postId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteOwnDraftPostReturns204NoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Draft to delete",
                null, null, PostStatus.DRAFT, userId, null, null));

        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify soft delete - post still exists with DELETED status
        Optional<Post> deletedPost = postRepository.findById(post.getId());
        assertTrue(deletedPost.isPresent());
        assertEquals(PostStatus.DELETED, deletedPost.get().getStatus());
    }

    @Test
    void deletedPostIsNotReturnedByGetById() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Draft to delete",
                null, null, PostStatus.DRAFT, userId, null, null));

        // Delete the post
        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Try to get the deleted post
        mockMvc.perform(get("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedPostIsNotIncludedInGetAllPosts() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        postRepository.save(new Post(UUID.randomUUID(), "Draft to keep",
                null, null, PostStatus.DRAFT, userId, null, null));
        Post post2 = postRepository.save(new Post(UUID.randomUUID(), "Draft to delete",
                null, null, PostStatus.DRAFT, userId, null, null));

        // Delete post2
        mockMvc.perform(delete("/api/posts/{postId}", post2.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Get all posts - should only return post1
        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Draft to keep"));
    }

    @Test
    void deleteAnotherUsersDraftPostReturns403Forbidden() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String otherUserToken = TestJwtUtil.generateToken(otherUserId, "other.teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Someone else's draft",
                null, null, PostStatus.DRAFT, ownerId, null, null));

        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You can only modify your own posts"));
    }

    @Test
    void deleteNonExistentPostReturns404NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(delete("/api/posts/{postId}", nonExistentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Post not found with id: " + nonExistentId));
    }

    @Test
    void deleteAlreadyDeletedPostReturns404NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Draft to delete twice",
                null, null, PostStatus.DRAFT, userId, null, null));

        // First delete - should succeed
        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Second delete - should return 404
        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithInvalidUuidReturns400BadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(delete("/api/posts/not-a-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void cannotDeletePublishedPost() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post post = postRepository.save(new Post(UUID.randomUUID(), "Published post",
                null, null, PostStatus.PUBLISHED, userId, null, null));

        mockMvc.perform(delete("/api/posts/{postId}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Can only delete DRAFT posts"));
    }
}
