package com.example.posts_service.service;

import com.example.posts_service.dto.CreatePostRequest;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.model.Post;
import com.example.posts_service.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
}
