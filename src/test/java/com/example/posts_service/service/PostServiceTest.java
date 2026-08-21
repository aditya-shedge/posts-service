package com.example.posts_service.service;

import com.example.posts_service.dto.CreatePostRequest;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.dto.UpdatePostRequest;
import com.example.posts_service.exception.InvalidPostStatusException;
import com.example.posts_service.exception.PostNotFoundException;
import com.example.posts_service.exception.UnauthorizedPostAccessException;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.model.Role;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository);
    }

    // --- Create Post Tests ---

    @Test
    void creatingPostSetsStatusToDraft() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest("Announcement", null, null);

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost(request, userId);

        assertEquals(PostStatus.DRAFT, response.getStatus());
    }

    @Test
    void creatingPostSavesEntityWithGeneratedUuidAndReturnsResponseWithAllFields() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "Field trip announcement",
                "https://example.com/doc.pdf",
                "Contact teacher for queries"
        );

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost(request, userId);

        assertNotNull(response.getId());
        assertEquals("Field trip announcement", response.getText());
        assertEquals("https://example.com/doc.pdf", response.getAttachment());
        assertEquals("Contact teacher for queries", response.getRemarks());
        assertEquals(PostStatus.DRAFT, response.getStatus());
    }

    // --- Get All Posts Tests ---

    @Test
    void teacherGetsOwnPostsExcludingDeleted() {
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));

        Post draftPost = new Post(UUID.randomUUID(), "Draft", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());
        Post publishedPost = new Post(UUID.randomUUID(), "Published", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findAllByCreatedByAndStatusNotOrderByCreatedAtDesc(userId, PostStatus.DELETED))
                .thenReturn(List.of(draftPost, publishedPost));

        List<PostResponse> responses = postService.getAllPosts(teacher);

        assertEquals(2, responses.size());
    }

    @Test
    void moderatorGetsAllDraftPosts() {
        UUID moderatorId = UUID.randomUUID();
        UserPrincipal moderator = new UserPrincipal(moderatorId, "moderator", Set.of(Role.MODERATOR));

        UUID otherUserId = UUID.randomUUID();
        Post draftPost1 = new Post(UUID.randomUUID(), "Draft 1", null, null, PostStatus.DRAFT, otherUserId, LocalDateTime.now(), LocalDateTime.now());
        Post draftPost2 = new Post(UUID.randomUUID(), "Draft 2", null, null, PostStatus.DRAFT, otherUserId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.DRAFT))
                .thenReturn(List.of(draftPost1, draftPost2));

        List<PostResponse> responses = postService.getAllPosts(moderator);

        assertEquals(2, responses.size());
    }

    // --- Update Post Tests ---

    @Test
    void teacherCanUpdateOwnDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated", null, null);

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.updatePost(postId, request, teacher);

        assertEquals("Updated", response.getText());
    }

    @Test
    void teacherCannotUpdateNonDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated", null, null);

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));

        assertThrows(InvalidPostStatusException.class, () -> postService.updatePost(postId, request, teacher));
    }

    @Test
    void teacherCannotUpdateAnotherUsersPost() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(otherUserId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, ownerId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated", null, null);

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));

        assertThrows(UnauthorizedPostAccessException.class, () -> postService.updatePost(postId, request, teacher));
    }

    // --- Delete Post Tests ---

    @Test
    void teacherCanDeleteOwnDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Draft", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.deletePost(postId, teacher);

        assertEquals(PostStatus.DELETED, existingPost.getStatus());
        verify(postRepository).save(existingPost);
    }

    @Test
    void teacherCannotDeleteNonDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Published", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));

        assertThrows(InvalidPostStatusException.class, () -> postService.deletePost(postId, teacher));
    }

    // --- Approve Post Tests ---

    @Test
    void moderatorCanApproveDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UserPrincipal moderator = new UserPrincipal(moderatorId, "moderator", Set.of(Role.MODERATOR));
        Post existingPost = new Post(postId, "Draft", null, null, PostStatus.DRAFT, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.approvePost(postId, moderator);

        assertEquals(PostStatus.PUBLISHED, response.getStatus());
    }

    @Test
    void moderatorCannotApproveNonDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UserPrincipal moderator = new UserPrincipal(moderatorId, "moderator", Set.of(Role.MODERATOR));
        Post existingPost = new Post(postId, "Published", null, null, PostStatus.PUBLISHED, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));

        assertThrows(InvalidPostStatusException.class, () -> postService.approvePost(postId, moderator));
    }

    @Test
    void teacherCannotApprovePosts() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));

        assertThrows(UnauthorizedPostAccessException.class, () -> postService.approvePost(postId, teacher));
    }

    // --- Reject Post Tests ---

    @Test
    void moderatorCanRejectDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UserPrincipal moderator = new UserPrincipal(moderatorId, "moderator", Set.of(Role.MODERATOR));
        Post existingPost = new Post(postId, "Draft", null, null, PostStatus.DRAFT, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.rejectPost(postId, moderator);

        assertEquals(PostStatus.REJECTED, response.getStatus());
    }

    @Test
    void teacherCannotRejectPosts() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));

        assertThrows(UnauthorizedPostAccessException.class, () -> postService.rejectPost(postId, teacher));
    }
}
