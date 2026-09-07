package com.example.posts_service.service;

import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.exception.InvalidPostStatusException;
import com.example.posts_service.exception.PostNotFoundException;
import com.example.posts_service.exception.UnauthorizedPostAccessException;
import com.example.posts_service.messaging.ModerationEventProducer;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.model.Role;
import com.example.posts_service.repository.AttachmentRepository;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private FileValidationService fileValidationService;

    @Mock
    private ModerationEventProducer moderationEventProducer;

    private PostService postService;

    @BeforeEach
    void setUp() throws Exception {
        postService = new PostService(postRepository, attachmentRepository, cloudinaryService, fileValidationService, moderationEventProducer);
        // inject tempDir since @Value is not processed outside Spring context
        java.lang.reflect.Field field = PostService.class.getDeclaredField("tempDir");
        field.setAccessible(true);
        field.set(postService, System.getProperty("java.io.tmpdir") + "/posts-attachments-test");
        // default: no attachment found for any post (lenient to avoid unused stub errors)
        org.mockito.Mockito.lenient()
                .when(attachmentRepository.findByPostId(any()))
                .thenReturn(Optional.empty());
    }

    // --- Create Post Tests ---

    @Test
    void creatingPostWithoutAttachmentSetsStatusToDraft() {
        UUID userId = UUID.randomUUID();

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost("Announcement", null, null, userId);

        assertEquals(PostStatus.DRAFT, response.getStatus());
        assertNull(response.getAttachmentStatus());
    }

    @Test
    void creatingPostWithAttachmentValidatesFile() throws IOException {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("attachment", "test.jpg", "image/jpeg", "content".getBytes());

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudinaryService.upload(any())).thenReturn(new com.example.posts_service.dto.CloudinaryUploadResult("https://cloudinary.com/test.jpg", "posts/abc123"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        postService.createPost("Announcement", null, file, userId);

        verify(fileValidationService).validate(file);
    }

    @Test
    void creatingPostWithAttachmentUploadsToCloudinaryAndSavesAttachment() throws IOException {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("attachment", "test.jpg", "image/jpeg", "content".getBytes());

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudinaryService.upload(any())).thenReturn(new com.example.posts_service.dto.CloudinaryUploadResult("https://cloudinary.com/test.jpg", "posts/abc123"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        postService.createPost("Announcement", null, file, userId);

        verify(attachmentRepository).save(any());
    }

    @Test
    void creatingPostPublishesModerationEvent() {
        UUID userId = UUID.randomUUID();

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.createPost("Field trip announcement", null, null, userId);

        verify(moderationEventProducer).publish(any(), any());
    }

    @Test
    void cloudinaryFailureSetsAttachmentStatusToPending() throws IOException {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("attachment", "test.jpg", "image/jpeg", "content".getBytes());

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudinaryService.upload(any())).thenThrow(new IOException("Cloudinary unavailable"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            com.example.posts_service.model.PostAttachment saved = invocation.getArgument(0);
            when(attachmentRepository.findByPostId(any())).thenReturn(Optional.of(saved));
            return saved;
        });

        PostResponse response = postService.createPost("Announcement", null, file, userId);

        assertEquals(com.example.posts_service.model.AttachmentStatus.PENDING, response.getAttachmentStatus());
        assertEquals("test.jpg", response.getAttachmentFilename());
    }

    @Test
    void creatingPostWithTextOnlyReturnsResponseWithAllFields() {
        UUID userId = UUID.randomUUID();

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.createPost("Field trip announcement", "Contact teacher", null, userId);

        assertNotNull(response.getId());
        assertEquals("Field trip announcement", response.getText());
        assertEquals("Contact teacher", response.getRemarks());
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

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.updatePost(postId, "Updated", null, null, false, teacher);

        assertEquals("Updated", response.getText());
    }

    @Test
    void teacherCannotUpdateNonDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.PUBLISHED, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));

        assertThrows(InvalidPostStatusException.class,
                () -> postService.updatePost(postId, "Updated", null, null, false, teacher));
    }

    @Test
    void teacherCannotUpdateAnotherUsersPost() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(otherUserId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, ownerId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));

        assertThrows(UnauthorizedPostAccessException.class,
                () -> postService.updatePost(postId, "Updated", null, null, false, teacher));
    }

    @Test
    void updatingTextOnlyLeavesAttachmentUnchanged() throws IOException {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.updatePost(postId, "Updated text", null, null, false, teacher);

        verify(cloudinaryService, org.mockito.Mockito.never()).upload(any());
        verify(cloudinaryService, org.mockito.Mockito.never()).delete(any());
        verify(attachmentRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void replacingAttachmentUploadsNewAndDeletesOld() throws IOException {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());
        MockMultipartFile newFile = new MockMultipartFile("attachment", "new.jpg", "image/jpeg", "content".getBytes());

        com.example.posts_service.model.PostAttachment existingAttachment =
                new com.example.posts_service.model.PostAttachment(postId, "old.jpg",
                        com.example.posts_service.model.AttachmentStatus.UPLOADED, 0);
        existingAttachment.setPublicId("posts/old-public-id");

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentRepository.findByPostId(postId)).thenReturn(Optional.of(existingAttachment));
        when(cloudinaryService.upload(any())).thenReturn(
                new com.example.posts_service.dto.CloudinaryUploadResult("https://cloudinary.com/new.jpg", "posts/new-id"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        postService.updatePost(postId, "Updated", null, newFile, false, teacher);

        verify(cloudinaryService).delete("posts/old-public-id");
        verify(cloudinaryService).upload(newFile);
        verify(attachmentRepository).delete(existingAttachment);
    }

    @Test
    void removingAttachmentDeletesFromCloudinaryAndClearsRecord() throws IOException {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());

        com.example.posts_service.model.PostAttachment existingAttachment =
                new com.example.posts_service.model.PostAttachment(postId, "file.jpg",
                        com.example.posts_service.model.AttachmentStatus.UPLOADED, 0);
        existingAttachment.setPublicId("posts/abc123");

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentRepository.findByPostId(postId)).thenReturn(Optional.of(existingAttachment));

        postService.updatePost(postId, "Updated", null, null, true, teacher);

        verify(cloudinaryService).delete("posts/abc123");
        verify(attachmentRepository).delete(existingAttachment);
    }

    @Test
    void cloudinaryDeletionFailureDoesNotBlockUpdate() throws IOException {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Original", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());
        MockMultipartFile newFile = new MockMultipartFile("attachment", "new.jpg", "image/jpeg", "content".getBytes());

        com.example.posts_service.model.PostAttachment existingAttachment =
                new com.example.posts_service.model.PostAttachment(postId, "old.jpg",
                        com.example.posts_service.model.AttachmentStatus.UPLOADED, 0);
        existingAttachment.setPublicId("posts/old-id");

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentRepository.findByPostId(postId)).thenReturn(Optional.of(existingAttachment));
        org.mockito.Mockito.doThrow(new IOException("Cloudinary down")).when(cloudinaryService).delete(any());
        when(cloudinaryService.upload(any())).thenReturn(
                new com.example.posts_service.dto.CloudinaryUploadResult("https://cloudinary.com/new.jpg", "posts/new-id"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Should not throw
        postService.updatePost(postId, "Updated", null, newFile, false, teacher);

        verify(cloudinaryService).upload(newFile);
        verify(attachmentRepository).save(any());
    }

    // --- Delete Post Tests ---

    @Test
    void teacherCanDeleteOwnDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal teacher = new UserPrincipal(userId, "teacher", Set.of(Role.TEACHER));
        Post existingPost = new Post(postId, "Draft", null, null, PostStatus.DRAFT, userId, LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
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

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));

        assertThrows(InvalidPostStatusException.class, () -> postService.deletePost(postId, teacher));
    }

    // --- Approve Post Tests ---

    @Test
    void moderatorCanApproveDraftPost() {
        UUID postId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UserPrincipal moderator = new UserPrincipal(moderatorId, "moderator", Set.of(Role.MODERATOR));
        Post existingPost = new Post(postId, "Draft", null, null, PostStatus.DRAFT, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
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

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));

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

        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(existingPost));
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
