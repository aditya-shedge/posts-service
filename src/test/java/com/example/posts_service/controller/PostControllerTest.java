package com.example.posts_service.controller;

import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.model.Role;
import com.example.posts_service.security.JwtTokenProvider;
import com.example.posts_service.security.UserPrincipal;
import com.example.posts_service.service.PostService;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
                new PostResponse(UUID.randomUUID(), "First post", null, null, null, null, PostStatus.DRAFT, null, null, userId, LocalDateTime.now(), LocalDateTime.now()),
                new PostResponse(UUID.randomUUID(), "Second post", null, null, null, null, PostStatus.DRAFT, null, null, userId, LocalDateTime.now(), LocalDateTime.now())
        );
        when(postService.getAllPosts(any(UserPrincipal.class))).thenReturn(mockPosts);

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("First post"))
                .andExpect(jsonPath("$[1].text").value("Second post"));
    }

    @Test
    void createPostWithTextOnlyReturns201() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        PostResponse mockResponse = new PostResponse(postId, "Field trip announcement", null, null, null, null, PostStatus.DRAFT, null, null, userId, LocalDateTime.now(), LocalDateTime.now());
        when(postService.createPost(any(), any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Field trip announcement")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.text").value("Field trip announcement"));
    }

    @Test
    void createPostWithAttachmentReturns201WithAttachmentUrl() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        MockMultipartFile file = new MockMultipartFile("attachment", "test.jpg", "image/jpeg", "content".getBytes());

        PostResponse mockResponse = new PostResponse(postId, "Announcement", "https://cloudinary.com/test.jpg", "test.jpg", AttachmentStatus.UPLOADED, null, PostStatus.DRAFT, null, null, userId, LocalDateTime.now(), LocalDateTime.now());
        when(postService.createPost(any(), any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/posts")
                        .file(file)
                        .param("text", "Announcement")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachment").value("https://cloudinary.com/test.jpg"))
                .andExpect(jsonPath("$.attachmentFilename").value("test.jpg"))
                .andExpect(jsonPath("$.attachmentStatus").value("UPLOADED"));
    }

    @Test
    void createPostWithMissingTextReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        mockMvc.perform(multipart("/api/posts")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validUpdateRequestReturns200WithUpdatedPost() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId);

        PostResponse mockResponse = new PostResponse(postId, "Updated text", null, null, null, "New remarks", PostStatus.DRAFT, null, null, userId, LocalDateTime.now(), LocalDateTime.now());
        when(postService.updatePost(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/posts/{postId}", postId)
                        .param("text", "Updated text")
                        .param("remarks", "New remarks")
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + TestJwtUtil.generateToken(userId, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Updated text"));
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
    void approvePostReturns200WithPublishedStatus() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId, Set.of(Role.MODERATOR));

        PostResponse mockResponse = new PostResponse(postId, "Approved post", null, null, null, null, PostStatus.PUBLISHED, null, null, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());
        when(postService.approvePost(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/posts/{postId}/approve", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateModeratorToken(userId, "moderator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void rejectPostReturns200WithRejectedStatus() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setAuthenticatedUser(userId, Set.of(Role.MODERATOR));

        PostResponse mockResponse = new PostResponse(postId, "Rejected post", null, null, null, null, PostStatus.REJECTED, null, null, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());
        when(postService.rejectPost(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/posts/{postId}/reject", postId)
                        .header("Authorization", "Bearer " + TestJwtUtil.generateModeratorToken(userId, "moderator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private void setAuthenticatedUser(UUID userId) {
        setAuthenticatedUser(userId, Set.of(Role.TEACHER));
    }

    private void setAuthenticatedUser(UUID userId, Set<Role> roles) {
        UserPrincipal principal = new UserPrincipal(userId, "teacher", roles);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
