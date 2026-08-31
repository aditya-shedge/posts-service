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

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CreatePostIntegrationTest extends BaseIntegrationTest {

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
    void createPostWithAllFieldsReturns201AndPersistsPost() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        String responseBody = mockMvc.perform(multipart("/api/posts")
                        .param("text", "Field trip to Science Museum")
                        .param("remarks", "Please sign by Thursday")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.text").value("Field trip to Science Museum"))
                .andExpect(jsonPath("$.remarks").value("Please sign by Thursday"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String postId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");
        Optional<Post> savedPost = postRepository.findById(UUID.fromString(postId));

        assertTrue(savedPost.isPresent());
        assertEquals("Field trip to Science Museum", savedPost.get().getText());
        assertEquals("Please sign by Thursday", savedPost.get().getRemarks());
        assertEquals(PostStatus.DRAFT, savedPost.get().getStatus());
        assertEquals(userId, savedPost.get().getCreatedBy());
    }

    @Test
    void createPostWithTextOnlyReturns201WithNullOptionalFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Simple announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.text").value("Simple announcement"))
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andExpect(jsonPath("$.remarks").doesNotExist())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createPostWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Announcement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createdPostIsReturnedInGetAllPosts() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "New announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("New announcement"));
    }

    @Test
    void createPostReturns201ImmediatelyWithDraftStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Field trip announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()));
    }
}
