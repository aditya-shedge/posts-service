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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        String responseBody = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Field trip to Science Museum",
                                  "attachment": "https://school.example.com/permission-slip.pdf",
                                  "remarks": "Please sign by Thursday"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.text").value("Field trip to Science Museum"))
                .andExpect(jsonPath("$.attachment").value("https://school.example.com/permission-slip.pdf"))
                .andExpect(jsonPath("$.remarks").value("Please sign by Thursday"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID and verify persistence
        String postId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");
        Optional<Post> savedPost = postRepository.findById(UUID.fromString(postId));

        assertTrue(savedPost.isPresent());
        assertEquals("Field trip to Science Museum", savedPost.get().getText());
        assertEquals("https://school.example.com/permission-slip.pdf", savedPost.get().getAttachment());
        assertEquals("Please sign by Thursday", savedPost.get().getRemarks());
        assertEquals(PostStatus.DRAFT, savedPost.get().getStatus());
        assertEquals(userId, savedPost.get().getCreatedBy());
    }

    @Test
    void createPostWithTextOnlyReturns201WithNullOptionalFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Simple announcement"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.text").value("Simple announcement"))
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andExpect(jsonPath("$.remarks").doesNotExist())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createPostWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Announcement"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createdPostIsReturnedInGetAllPosts() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        // Create a post
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "New announcement"
                                }
                                """))
                .andExpect(status().isCreated());

        // Verify it appears in the list
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/posts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("New announcement"));
    }
}
