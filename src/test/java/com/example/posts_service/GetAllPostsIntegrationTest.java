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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GetAllPostsIntegrationTest extends BaseIntegrationTest {

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
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherSeesOwnPostsOrderedByCreationDateDescending() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post olderPost = new Post(UUID.randomUUID(), "Older announcement", null, null, PostStatus.DRAFT, userId, null, null);
        Post newerPost = new Post(UUID.randomUUID(), "Newer announcement", null, null, PostStatus.DRAFT, userId, null, null);

        postRepository.save(olderPost);
        Thread.sleep(10);
        postRepository.save(newerPost);

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].text").value("Newer announcement"))
                .andExpect(jsonPath("$.content[1].text").value("Older announcement"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void teacherSeesOnlyOwnPosts() throws Exception {
        UUID teacherOneId = UUID.randomUUID();
        UUID teacherTwoId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(teacherOneId, "teacher.one");

        postRepository.save(new Post(UUID.randomUUID(), "Post by teacher one", null, null, PostStatus.DRAFT, teacherOneId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Post by teacher two", null, null, PostStatus.DRAFT, teacherTwoId, null, null));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].text").value("Post by teacher one"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void teacherSeesOwnPostsAllStatusesExceptDeleted() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        postRepository.save(new Post(UUID.randomUUID(), "Draft post", null, null, PostStatus.DRAFT, userId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Published post", null, null, PostStatus.PUBLISHED, userId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Rejected post", null, null, PostStatus.REJECTED, userId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Deleted post", null, null, PostStatus.DELETED, userId, null, null));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void moderatorSeesAllDraftPosts() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID teacherOneId = UUID.randomUUID();
        UUID teacherTwoId = UUID.randomUUID();
        String token = TestJwtUtil.generateModeratorToken(moderatorId, "moderator");

        postRepository.save(new Post(UUID.randomUUID(), "Draft by teacher one", null, null, PostStatus.DRAFT, teacherOneId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Draft by teacher two", null, null, PostStatus.DRAFT, teacherTwoId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Published post", null, null, PostStatus.PUBLISHED, teacherOneId, null, null));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void responseContainsPaginationMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        postRepository.save(new Post(UUID.randomUUID(), "Post", null, null, PostStatus.DRAFT, userId, null, null));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void secondPageReturnsCorrectSubset() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        for (int i = 0; i < 12; i++) {
            postRepository.save(new Post(UUID.randomUUID(), "Post " + i, null, null, PostStatus.DRAFT, userId, null, null));
        }

        mockMvc.perform(get("/api/posts?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void pageExceedingTotalReturnsEmptyContent() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        postRepository.save(new Post(UUID.randomUUID(), "Post", null, null, PostStatus.DRAFT, userId, null, null));

        mockMvc.perform(get("/api/posts?page=99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customPageSizeLimitsResults() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        for (int i = 0; i < 8; i++) {
            postRepository.save(new Post(UUID.randomUUID(), "Post " + i, null, null, PostStatus.DRAFT, userId, null, null));
        }

        mockMvc.perform(get("/api/posts?size=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(5));
    }
}
