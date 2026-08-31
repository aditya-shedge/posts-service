# Delete Post Implementation Plan

## Overview

Implement `DELETE /api/posts/{id}` endpoint with soft delete. Changes post status to `DELETED` instead of removing from database. Update existing queries to filter by `PUBLISHED` status so deleted posts are not visible.

## Architecture

```
DELETE /api/posts/{id} → PostController.deletePost() → PostService.deletePost()
    → Find post by ID and PUBLISHED status
    → Authorization check (createdBy == userId)
    → Set status = DELETED
    → JPA @PreUpdate sets updatedAt
    → Return 204 No Content
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**: 
- `src/main/java/com/example/posts_service/repository/PostRepository.java`
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/repository/PostRepositoryTest.java`
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

**What to do in this phase:**
- Verify existing tests pass with `./gradlew test --rerun`
- Confirm existing repository and service methods have test coverage
- Note: Existing tests already use PUBLISHED status, so they will continue to work after we add status filtering

**High-level characterization coverage:**
- Existing getAllPosts returns posts (will be updated to filter by PUBLISHED)
- Existing getPostById returns post (will be updated to filter by PUBLISHED)

---

### Phase 2: Repository Layer - Add Status-Filtered Queries

**Files**: 
- `src/main/java/com/example/posts_service/repository/PostRepository.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/repository/PostRepositoryTest.java`

Add new repository methods that filter by status.

**Key code changes:**

```java
// existing code
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByOrderByCreatedAtDesc();
}

// new code
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByOrderByCreatedAtDesc();
    
    List<Post> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    
    Optional<Post> findByIdAndStatus(UUID id, PostStatus status);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Finding all posts by PUBLISHED status returns only published posts
- Finding all posts by PUBLISHED status excludes deleted posts
- Finding post by ID and PUBLISHED status returns the post
- Finding post by ID and PUBLISHED status returns empty when post is deleted

**Technical details:**
- Spring Data JPA generates queries from method names
- Import `Optional` and `PostStatus` in repository

---

### Phase 3: Service Layer - Update Existing Methods and Add Delete

**Files**: 
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

Update `getAllPosts()` and `getPostById()` to filter by PUBLISHED status. Add `deletePost()` method.

**Key code changes:**

```java
// existing code
public List<PostResponse> getAllPosts() {
    return postRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
}

public PostResponse getPostById(UUID postId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
    return toResponse(post);
}

// new code
public List<PostResponse> getAllPosts() {
    return postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED).stream()
            .map(this::toResponse)
            .toList();
}

public PostResponse getPostById(UUID postId) {
    Post post = postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)
            .orElseThrow(() -> new PostNotFoundException(postId));
    return toResponse(post);
}

public void deletePost(UUID postId, UUID userId) {
    Post post = postRepository.findByIdAndStatus(postId, PostStatus.PUBLISHED)
            .orElseThrow(() -> new PostNotFoundException(postId));

    if (!post.getCreatedBy().equals(userId)) {
        throw new UnauthorizedPostAccessException(postId);
    }

    post.setStatus(PostStatus.DELETED);
    postRepository.save(post);
    log.info("Deleted post: id={}, deletedBy={}", postId, userId);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Deleting own post changes status to DELETED
- Deleting non-existent post throws PostNotFoundException
- Deleting another user's post throws UnauthorizedPostAccessException
- Deleting already-deleted post throws PostNotFoundException

**Technical details:**
- `deletePost()` returns void (204 No Content)
- `@PreUpdate` automatically updates `updatedAt` timestamp
- Reuse `UnauthorizedPostAccessException` from Story 004

---

### Phase 4: Controller Layer

**Files**: 
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

Add `DELETE /api/posts/{id}` endpoint.

**Key code changes:**

```java
// existing code
@PutMapping("/{postId}")
public ResponseEntity<PostResponse> updatePost(...) {
    ...
}

// new code
@DeleteMapping("/{postId}")
public ResponseEntity<Void> deletePost(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserPrincipal principal) {
    postService.deletePost(postId, principal.getUserId());
    return ResponseEntity.noContent().build();
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Valid delete request returns 204 No Content
- Delete with invalid UUID path returns 400

**Technical details:**
- Add `DeleteMapping` import
- Returns `ResponseEntity<Void>` with 204 status
- No response body on success

---

### Phase 5: Integration Tests

**Files**: None

**Test Files**: 
- `src/test/java/com/example/posts_service/DeletePostIntegrationTest.java` (new)

Full stack integration tests with real database.

**Test scenarios (implement exactly these; no extra tests):**
- Successfully delete own post returns 204 No Content
- Deleted post is not returned by GET /api/posts/{id} (returns 404)
- Deleted post is not included in GET /api/posts list
- Delete another user's post returns 403 Forbidden
- Delete non-existent post returns 404 Not Found
- Delete already-deleted post returns 404 Not Found
- Delete with invalid UUID returns 400 Bad Request
- Delete without authorization header returns 401 Unauthorized

**Technical details:**
- Follow existing integration test pattern with `@BeforeEach` and `@AfterEach` cleanup
- Use `TestJwtUtil` to generate tokens
- Verify soft delete by checking post still exists in database with DELETED status

---

## Technical Considerations

- **Dependencies**: No new dependencies required
- **Edge Cases**: 
  - Deleting already-deleted post (returns 404, not 403)
  - Concurrent delete attempts (last one wins, both return 204 or second returns 404)
- **Testing Strategy**: Unit tests for repository/service/controller, integration tests for full stack
- **Performance**: Single database read + write, indexed query on id + status
- **Security**: Authorization check prevents cross-user deletion

## Testing Notes

- Write tests alongside the production changes for each phase
- Run tests for each phase before moving to the next
- Existing tests should continue to pass since they create PUBLISHED posts

## Success Criteria

- [ ] DELETE /api/posts/{id} returns 204 for owner
- [ ] DELETE /api/posts/{id} returns 403 when non-owner attempts delete
- [ ] DELETE /api/posts/{id} returns 404 for non-existent post
- [ ] DELETE /api/posts/{id} returns 404 for already-deleted post
- [ ] DELETE /api/posts/{id} returns 400 for invalid UUID format
- [ ] DELETE /api/posts/{id} returns 401 without auth header
- [ ] GET /api/posts excludes deleted posts
- [ ] GET /api/posts/{id} returns 404 for deleted post
- [ ] Post record remains in database with status=DELETED
- [ ] All existing tests continue to pass
