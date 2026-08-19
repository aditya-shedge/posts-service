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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GetAllPostsIntegrationTest {

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
    void multiplePostsAreReturnedOrderedByCreationDateDescending() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        Post olderPost = new Post(UUID.randomUUID(), "Older announcement", null, null, PostStatus.PUBLISHED, userId, null, null);
        Post newerPost = new Post(UUID.randomUUID(), "Newer announcement", null, null, PostStatus.PUBLISHED, userId, null, null);

        postRepository.save(olderPost);
        Thread.sleep(10);
        postRepository.save(newerPost);

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Newer announcement"))
                .andExpect(jsonPath("$[1].text").value("Older announcement"));
    }

    @Test
    void postsFromDifferentCreatorsAreAllReturned() throws Exception {
        UUID teacherOneId = UUID.randomUUID();
        UUID teacherTwoId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(teacherOneId, "teacher.one");

        postRepository.save(new Post(UUID.randomUUID(), "Post by teacher one", null, null, PostStatus.PUBLISHED, teacherOneId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Post by teacher two", null, null, PostStatus.PUBLISHED, teacherTwoId, null, null));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
