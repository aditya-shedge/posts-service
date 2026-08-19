package com.example.posts_service.service;

import com.example.posts_service.dto.CreatePostRequest;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.dto.UpdatePostRequest;
import com.example.posts_service.exception.PostNotFoundException;
import com.example.posts_service.exception.UnauthorizedPostAccessException;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    @Test
    void fetchingAllPostsReturnsListMappedToPostResponseInCorrectOrder() {
        UUID userId = UUID.randomUUID();
        LocalDateTime older = LocalDateTime.now().minusHours(1);
        LocalDateTime newer = LocalDateTime.now();

        Post newerPost = new Post(UUID.randomUUID(), "Newer post", null, null, PostStatus.PUBLISHED, userId, newer, newer);
        Post olderPost = new Post(UUID.randomUUID(), "Older post", null, null, PostStatus.PUBLISHED, userId, older, older);

        when(postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED)).thenReturn(List.of(newerPost, olderPost));

        List<PostResponse> responses = postService.getAllPosts();

        assertEquals(2, responses.size());
        assertEquals("Newer post", responses.get(0).getText());
        assertEquals("Older post", responses.get(1).getText());
    }

    @Test
    void fetchingPostsWhenNoneExistReturnsEmptyList() {
        when(postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED)).thenReturn(List.of());

        List<PostResponse> responses = postService.getAllPosts();

        assertTrue(responses.isEmpty());
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
    }

    @Test
    void creatingPostWithNullAttachmentAndRemarksSavesCorrectly() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest("Simple announcement", null, null);

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost(request, userId);

        assertNull(response.getAttachment());
        assertNull(response.getRemarks());
    }

    @Test
    void createdPostResponseContainsAuthenticatedUserIdAsCreatedBy() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest("Announcement", null, null);

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost(request, userId);

        assertEquals(userId, response.getCreatedBy());
    }

    @Test
    void updatingOwnPostReturnsUpdatedPostResponse() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
        Post existingPost = new Post(postId, "Original text", "https://old.com/doc.pdf", "Old remarks",
                PostStatus.PUBLISHED, userId, createdAt, createdAt);

        UpdatePostRequest request = new UpdatePostRequest("Updated text", "https://new.com/doc.pdf", "New remarks");

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.updatePost(postId, request, userId);

        assertEquals("Updated text", response.getText());
        assertEquals("https://new.com/doc.pdf", response.getAttachment());
        assertEquals("New remarks", response.getRemarks());
    }

    @Test
    void updatingNonExistentPostThrowsPostNotFoundException() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdatePostRequest request = new UpdatePostRequest("Updated text", null, null);

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.updatePost(postId, request, userId));
    }

    @Test
    void updatingAnotherUsersPostThrowsUnauthorizedPostAccessException() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Post existingPost = new Post(postId, "Original text", null, null,
                PostStatus.PUBLISHED, ownerId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated text", null, null);

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));

        assertThrows(UnauthorizedPostAccessException.class, () -> postService.updatePost(postId, request, otherUserId));
    }

    @Test
    void updatingPostWithNullAttachmentClearsAttachment() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Post existingPost = new Post(postId, "Original text", "https://old.com/doc.pdf", null,
                PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated text", null, null);

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.updatePost(postId, request, userId);

        assertNull(response.getAttachment());
    }

    @Test
    void updatingPostWithNullRemarksClearsRemarks() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Post existingPost = new Post(postId, "Original text", null, "Old remarks",
                PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        UpdatePostRequest request = new UpdatePostRequest("Updated text", null, null);

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.updatePost(postId, request, userId);

        assertNull(response.getRemarks());
    }

    @Test
    void deletingOwnPostChangesStatusToDeleted() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Post existingPost = new Post(postId, "Post to delete", null, null,
                PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.deletePost(postId, userId);

        assertEquals(PostStatus.DELETED, existingPost.getStatus());
        verify(postRepository).save(existingPost);
    }

    @Test
    void deletingNonExistentPostThrowsPostNotFoundException() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.deletePost(postId, userId));
    }

    @Test
    void deletingAnotherUsersPostThrowsUnauthorizedPostAccessException() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Post existingPost = new Post(postId, "Post to delete", null, null,
                PostStatus.PUBLISHED, ownerId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.of(existingPost));

        assertThrows(UnauthorizedPostAccessException.class, () -> postService.deletePost(postId, otherUserId));
    }

    @Test
    void deletingAlreadyDeletedPostThrowsPostNotFoundException() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.deletePost(postId, userId));
    }
}
