# Story 002: View All Posts - Implementation Plan

## Overview

Implement the `GET /api/posts` endpoint that returns all posts ordered by creation date descending. All foundational infrastructure (JWT security, error handling, PostResponse DTO, PostRepository) is already in place from Story 001. This story requires only a repository query method, a service method, and a controller endpoint.

## Architecture

```
GET /api/posts
     ↓
[JWT Authentication Filter] → Already in place
     ↓
[PostController.getAllPosts()] → New endpoint
     ↓
[PostService.getAllPosts()] → New method
     ↓
[PostRepository.findAllByOrderByCreatedAtDesc()] → New query method
     ↓
[PostgreSQL] → posts table
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers (including in test names and comments).
- Implement ONLY the test scenarios defined in the plan. Do not add extra tests unless the plan explicitly requires them.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**:
- `src/main/java/com/example/posts_service/repository/PostRepository.java`
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/repository/PostRepositoryTest.java`
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

**What to do in this phase:**

- Run the existing test suite and confirm all tests pass before making any changes.
- Identify that none of the existing tests cover a `getAllPosts` behaviour since it doesn't exist yet — no characterization tests needed.

**High-level characterization coverage:**

- Existing create post tests pass without regression

---

### Phase 2: Repository Query, Service Method, and Controller Endpoint

**Files**:
- `src/main/java/com/example/posts_service/repository/PostRepository.java`
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`
- `src/test/java/com/example/posts_service/GetAllPostsIntegrationTest.java`

**Description:**
Add the `findAllByOrderByCreatedAtDesc` query method to the repository, a `getAllPosts` method to the service, and a `GET /api/posts` endpoint to the controller.

**Key code changes:**

```java
// existing code - PostRepository.java
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
}

// new code - PostRepository.java
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByOrderByCreatedAtDesc();
}
```

```java
// existing code - PostService.java
public PostResponse createPost(CreatePostRequest request, UUID userId) { ... }

// new code - PostService.java
public List<PostResponse> getAllPosts() {
    return postRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
}
```

```java
// existing code - PostController.java
@PostMapping
public ResponseEntity<PostResponse> createPost(...) { ... }

// new code - PostController.java
@GetMapping
public ResponseEntity<List<PostResponse>> getAllPosts() {
    return ResponseEntity.ok(postService.getAllPosts());
}
```

**Test scenarios (implement exactly these; no extra tests):**

**Unit tests - PostServiceTest:**
- Fetching all posts returns a list mapped to PostResponse in correct order (most recent first)
- Fetching posts when none exist returns an empty list

**Unit tests - PostControllerTest (MockMvc):**
- GET /api/posts returns 200 OK with list of post responses
- GET /api/posts returns 200 OK with empty list when no posts exist
- GET /api/posts without Authorization header returns 401 Unauthorized

**Integration tests - GetAllPostsIntegrationTest:**
- Multiple posts are returned ordered by creation date descending
- Posts from different creators are all returned

**Technical details and Assumptions:**

- Spring Data JPA derives the query from the method name `findAllByOrderByCreatedAtDesc`
- `List.toList()` (Java 16+) used instead of `Collectors.toList()`
- No pagination — all posts returned in a single response
- DELETED status posts are included (soft delete not yet implemented — that's a future concern)

---

## Technical Considerations

**Dependencies**: None — all dependencies already in place from Story 001.

**Edge Cases:**
- Empty table returns `[]` with 200 OK, not a 404
- Posts with identical `createdAt` values have undefined relative order (acceptable)

**Testing Strategy:**
- Unit tests use Mockito to mock the repository
- Integration tests use the real PostgreSQL database with `@AfterEach` cleanup

**Security**: JWT authentication already enforced globally by `SecurityConfig`.

---

## Testing Notes

- Run `./gradlew test` before starting to confirm baseline passes.
- Run `./gradlew test` again after implementation to confirm all tests pass.

---

## Success Criteria

- [ ] GET /api/posts returns 200 OK with all posts ordered by createdAt descending
- [ ] GET /api/posts returns 200 OK with empty array when no posts exist
- [ ] GET /api/posts without JWT token returns 401 Unauthorized
- [ ] Posts from all creators are included in the response
- [ ] All existing Story 001 tests continue to pass
- [ ] Application builds successfully with `./gradlew build`
