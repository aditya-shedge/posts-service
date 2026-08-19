package com.example.posts_service.controller;

import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.security.JwtTokenProvider;
import com.example.posts_service.security.UserPrincipal;
import com.example.posts_service.service.PostService;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllPostsReturns200WithListOfPostResponses() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        List<PostResponse> mockPosts = List.of(
                new PostResponse(UUID.randomUUID(), "First post", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now()),
                new PostResponse(UUID.randomUUID(), "Second post", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now())
        );
        when(postService.getAllPosts()).thenReturn(mockPosts);

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("First post"))
                .andExpect(jsonPath("$[1].text").value("Second post"));
    }

    @Test
    void getAllPostsReturns200WithEmptyListWhenNoPostsExist() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        when(postService.getAllPosts()).thenReturn(List.of());

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validRequestReturns201CreatedWithPostResponse() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        PostResponse mockResponse = new PostResponse(
                postId, "Field trip announcement",
                "https://example.com/doc.pdf", "Contact teacher",
                PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now()
        );
        when(postService.createPost(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Field trip announcement",
                                  "attachment": "https://example.com/doc.pdf",
                                  "remarks": "Contact teacher"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.text").value("Field trip announcement"));
    }

    @Test
    void missingTextReturns400WithValidationError() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachment": "https://example.com/doc.pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("text")));
    }

    @Test
    void invalidAttachmentUrlReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Announcement",
                                  "attachment": "not-a-valid-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void remarksThatExceed1000CharactersReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);
        String longRemarks = "a".repeat(1001);

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Announcement",
                                  "remarks": "%s"
                                }
                                """.formatted(longRemarks)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("remarks")));
    }

    @Test
    void validUpdateRequestReturns200WithUpdatedPost() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        PostResponse mockResponse = new PostResponse(
                postId, "Updated text", "https://new.com/doc.pdf", "New remarks",
                PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now()
        );
        when(postService.updatePost(any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text",
                                  "attachment": "https://new.com/doc.pdf",
                                  "remarks": "New remarks"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.text").value("Updated text"));
    }

    @Test
    void updateWithBlankTextReturns400ValidationError() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "",
                                  "attachment": "https://example.com/doc.pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("text")));
    }

    @Test
    void updateWithInvalidUrlReturns400ValidationError() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text",
                                  "attachment": "not-a-valid-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void updateWithInvalidUuidReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(put("/api/posts/not-a-uuid")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void validDeleteRequestReturns204NoContent() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(delete("/api/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteWithInvalidUuidReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(delete("/api/posts/not-a-uuid")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void requestWithMultipleInvalidFieldsReturnsAllErrorsInSingleResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "",
                                  "attachment": "not-a-valid-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("text")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("attachment")));
    }

    private void setAuthenticatedUser(UUID userId) {
        UserPrincipal principal = new UserPrincipal(userId, "teacher");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
