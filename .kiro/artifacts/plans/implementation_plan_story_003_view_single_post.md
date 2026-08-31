# Story 003: View Single Post - Implementation Plan

## Overview

Implement the `GET /api/posts/{id}` endpoint that returns a single post by its UUID. This story introduces `PostNotFoundException` for the 404 case and invalid UUID path variable handling for the 400 case. All other infrastructure is already in place from Stories 001 and 002.

## Architecture

```
GET /api/posts/{id}
     ↓
[JWT Authentication Filter] → Already in place
     ↓
[PostController.getPostById(UUID id)] → New endpoint
     ↓
[PostService.getPostById(UUID id)] → New method
     ↓
[PostRepository.findById(UUID)] → Already in place (JpaRepository)
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
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/controller/PostController.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

**What to do in this phase:**

- Run the existing test suite and confirm all tests pass before making any changes.
- Note that `GlobalExceptionHandler` currently has no handler for `PostNotFoundException` — this will be added in Phase 2.

**High-level characterization coverage:**

- Existing create post and get all posts tests pass without regression

---

### Phase 2: PostNotFoundException and Global Exception Handler Update

**Files**:
- `src/main/java/com/example/posts_service/exception/PostNotFoundException.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**:
- `src/test/java/com/example/posts_service/exception/GlobalExceptionHandlerTest.java`

**Description:**
Create the `PostNotFoundException` domain exception and add its handler to `GlobalExceptionHandler`. Also add a handler for invalid UUID path variables (`MethodArgumentTypeMismatchException`).

**Key code changes:**

```java
// new code - PostNotFoundException.java
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(UUID postId) {
        super(String.format("Post not found with id: %s", postId));
    }
}
```

```java
// existing code - GlobalExceptionHandler.java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationError(...) { ... }

// new code - GlobalExceptionHandler.java
@ExceptionHandler(PostNotFoundException.class)
public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
    log.info("Post not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(404, "Not Found", ex.getMessage()));
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    log.warn("Invalid path variable type: {}", ex.getMessage());
    return ResponseEntity.badRequest()
            .body(new ErrorResponse(400, "Bad Request", "Invalid post ID format"));
}
```

**Test scenarios (implement exactly these; no extra tests):**

- PostNotFoundException returns 404 with "Not Found" error and the post ID in the message
- MethodArgumentTypeMismatchException returns 400 with "Invalid post ID format" message

**Technical details and Assumptions:**

- `PostNotFoundException` extends `RuntimeException` (unchecked)
- Log at INFO level for 404 (expected business scenario, not an error)
- Log at WARN level for invalid UUID format

---

### Phase 3: Service Method, Controller Endpoint, and Tests

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`
- `src/test/java/com/example/posts_service/GetPostByIdIntegrationTest.java`

**Description:**
Add `getPostById` to the service (throws `PostNotFoundException` when not found) and `GET /api/posts/{id}` to the controller.

**Key code changes:**

```java
// existing code - PostService.java
public List<PostResponse> getAllPosts() { ... }

// new code - PostService.java
public PostResponse getPostById(UUID postId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
    return toResponse(post);
}
```

```java
// existing code - PostController.java
@GetMapping
public ResponseEntity<List<PostResponse>> getAllPosts() { ... }

// new code - PostController.java
@GetMapping("/{id}")
public ResponseEntity<PostResponse> getPostById(@PathVariable UUID id) {
    return ResponseEntity.ok(postService.getPostById(id));
}
```

**Test scenarios (implement exactly these; no extra tests):**

**Unit tests - PostServiceTest:**
- Fetching a post by a valid ID returns the correct post response
- Fetching a post with a non-existent ID throws PostNotFoundException

**Unit tests - PostControllerTest (MockMvc):**
- GET /api/posts/{id} with valid existing ID returns 200 OK with post response
- GET /api/posts/{id} with non-existent ID returns 404 Not Found
- GET /api/posts/{id} with invalid UUID format returns 400 Bad Request

**Integration tests - GetPostByIdIntegrationTest:**
- Saved post can be retrieved by its ID with all fields correct
- Requesting a non-existent post ID returns 404
- Any authenticated user can view a post created by a different user

**Technical details and Assumptions:**

- `@PathVariable UUID id` — Spring automatically converts the path string to UUID; invalid format triggers `MethodArgumentTypeMismatchException` which the handler catches
- `postRepository.findById()` is already provided by `JpaRepository`
- No ownership check — any authenticated user can view any post

---

## Technical Considerations

**Dependencies**: None — all dependencies already in place.

**Edge Cases:**
- Non-existent UUID (valid format but no matching record) → 404
- Invalid UUID format (e.g. `abc`, `123`) → 400
- Valid UUID with correct format → 200 or 404 depending on existence

**Testing Strategy:**
- Unit tests use Mockito to mock repository and service
- Integration tests use real PostgreSQL with `@AfterEach` cleanup

---

## Testing Notes

- Run `./gradlew test` before starting to confirm baseline passes.
- Run `./gradlew test` after each phase before moving to the next.

---

## Success Criteria

- [ ] GET /api/posts/{id} returns 200 OK with full post details for existing post
- [ ] GET /api/posts/{id} returns 404 with meaningful message for non-existent post
- [ ] GET /api/posts/{id} returns 400 for invalid UUID format in path
- [ ] Any authenticated user can view any post regardless of creator
- [ ] All existing Story 001 and 002 tests continue to pass
- [ ] Application builds successfully with `./gradlew build`
