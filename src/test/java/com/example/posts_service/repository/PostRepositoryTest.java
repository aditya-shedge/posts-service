package com.example.posts_service.repository;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class PostRepositoryTest extends com.example.posts_service.BaseIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Test
    void savingPostWithAllFieldsPersistsCorrectlyAndCanBeRetrievedById() {
        UUID id = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Post post = new Post(id, "Field trip announcement", "https://example.com/doc.pdf", "Contact teacher for queries", PostStatus.PUBLISHED, createdBy, null, null);
        postRepository.save(post);

        Optional<Post> retrieved = postRepository.findById(id);

        assertTrue(retrieved.isPresent());
        assertEquals(id, retrieved.get().getId());
        assertEquals("Field trip announcement", retrieved.get().getText());
        assertEquals("https://example.com/doc.pdf", retrieved.get().getAttachment());
        assertEquals("Contact teacher for queries", retrieved.get().getRemarks());
        assertEquals(createdBy, retrieved.get().getCreatedBy());
        assertNotNull(retrieved.get().getCreatedAt());
        assertNotNull(retrieved.get().getUpdatedAt());
    }

    @Test
    void savingPostWithNullAttachmentAndRemarksPersistsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Post post = new Post(id, "Simple announcement", null, null, PostStatus.PUBLISHED, createdBy, null, null);
        postRepository.save(post);

        Optional<Post> retrieved = postRepository.findById(id);

        assertTrue(retrieved.isPresent());
        assertEquals("Simple announcement", retrieved.get().getText());
        assertNull(retrieved.get().getAttachment());
        assertNull(retrieved.get().getRemarks());
    }

    @Test
    void findAllByStatusReturnsOnlyPublishedPosts() {
        UUID userId = UUID.randomUUID();
        postRepository.save(new Post(UUID.randomUUID(), "Published post", null, null, PostStatus.PUBLISHED, userId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Another published", null, null, PostStatus.PUBLISHED, userId, null, null));

        List<Post> results = postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        assertEquals(2, results.size());
    }

    @Test
    void findAllByStatusExcludesDeletedPosts() {
        UUID userId = UUID.randomUUID();
        postRepository.save(new Post(UUID.randomUUID(), "Published post", null, null, PostStatus.PUBLISHED, userId, null, null));
        postRepository.save(new Post(UUID.randomUUID(), "Deleted post", null, null, PostStatus.DELETED, userId, null, null));

        List<Post> results = postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        assertEquals(1, results.size());
        assertEquals("Published post", results.get(0).getText());
    }

    @Test
    void findByIdAndStatusReturnsPostWhenPublished() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        postRepository.save(new Post(postId, "Published post", null, null, PostStatus.PUBLISHED, userId, null, null));

        Optional<Post> result = postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED);

        assertTrue(result.isPresent());
        assertEquals("Published post", result.get().getText());
    }

    @Test
    void findByIdAndStatusReturnsEmptyWhenPostIsDeleted() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        postRepository.save(new Post(postId, "Deleted post", null, null, PostStatus.DELETED, userId, null, null));

        Optional<Post> result = postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED);

        assertTrue(result.isEmpty());
    }
}
