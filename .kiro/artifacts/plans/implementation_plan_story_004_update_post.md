# Update Post Implementation Plan

## Overview

Implement `PUT /api/posts/{id}` endpoint allowing authenticated teachers to update their own posts. Includes authorization check (403 if not owner), validation, and proper error handling.

## Architecture

```
PUT /api/posts/{id} → PostController.updatePost() → PostService.updatePost() 
    → Authorization check (createdBy == userId) 
    → Update Post entity fields 
    → JPA @PreUpdate sets updatedAt 
    → Return PostResponse
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
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
- Verify existing tests pass with `./gradlew test --rerun`
- Confirm existing service methods (createPost, getAllPosts, getPostById) have test coverage
- No new characterization tests needed since we're adding new functionality, not modifying existing behavior

**High-level characterization coverage:**
- Existing CRUD operations remain functional after adding update capability
- Existing exception handlers continue to work correctly

---

### Phase 2: Exception and DTO Setup

**Files**: 
- `src/main/java/com/example/posts_service/exception/UnauthorizedPostAccessException.java` (new)
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`
- `src/main/java/com/example/posts_service/dto/UpdatePostRequest.java` (new)

**Test Files**: None for this phase (tested via integration in Phase 4)

Create the authorization exception and update request DTO.

**Key code changes:**

```java
// new code - UnauthorizedPostAccessException.java
package com.example.posts_service.exception;

import java.util.UUID;

public class UnauthorizedPostAccessException extends RuntimeException {
    public UnauthorizedPostAccessException(UUID postId) {
        super("You can only modify your own posts");
    }
}
```

```java
// existing code - GlobalExceptionHandler.java
@ExceptionHandler(PostNotFoundException.class)
public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
    // ...
}

// new code - add after PostNotFoundException handler
@ExceptionHandler(UnauthorizedPostAccessException.class)
public ResponseEntity<ErrorResponse> handleUnauthorizedPostAccess(UnauthorizedPostAccessException ex) {
    log.warn("Unauthorized post access attempt: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(403, "Forbidden", ex.getMessage()));
}
```

```java
// new code - UpdatePostRequest.java (same validation as CreatePostRequest)
package com.example.posts_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public class UpdatePostRequest {

    @NotBlank(message = "Text is required")
    private String text;

    @URL(message = "Attachment must be a valid URL")
    private String attachment;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;

    // Default constructor, all-args constructor, getters, setters
}
```

**Technical details:**
- Follow existing `CreatePostRequest` pattern for validation annotations
- Exception message matches API spec: "You can only modify your own posts"

---

### Phase 3: Service Layer

**Files**: 
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

Add `updatePost()` method to PostService with authorization check.

**Key code changes:**

```java
// existing code
public PostResponse getPostById(UUID postId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
    return toResponse(post);
}

// new code
public PostResponse updatePost(UUID postId, UpdatePostRequest request, UUID userId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
    
    if (!post.getCreatedBy().equals(userId)) {
        throw new UnauthorizedPostAccessException(postId);
    }
    
    post.setText(request.getText());
    post.setAttachment(request.getAttachment());
    post.setRemarks(request.getRemarks());
    
    Post saved = postRepository.save(post);
    log.info("Updated post: id={}, updatedBy={}", saved.getId(), userId);
    
    return toResponse(saved);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Updating own post returns updated PostResponse with new field values
- Updating non-existent post throws PostNotFoundException
- Updating another user's post throws UnauthorizedPostAccessException
- Setting attachment to null clears the attachment field
- Setting remarks to null clears the remarks field

**Technical details:**
- Entity setters already exist on Post (`@Setter` on text, attachment, remarks)
- `@PreUpdate` handles updatedAt automatically
- Authorization check compares `post.getCreatedBy()` with authenticated `userId`

---

### Phase 4: Controller Layer

**Files**: 
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

Add `PUT /api/posts/{id}` endpoint.

**Key code changes:**

```java
// existing code
@GetMapping("/{postId}")
public ResponseEntity<PostResponse> getPostById(@PathVariable UUID postId) {
    return ResponseEntity.ok(postService.getPostById(postId));
}

// new code
@PutMapping("/{postId}")
public ResponseEntity<PostResponse> updatePost(
        @PathVariable UUID postId,
        @Valid @RequestBody UpdatePostRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    PostResponse response = postService.updatePost(postId, request, principal.getUserId());
    return ResponseEntity.ok(response);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Valid update request returns 200 with updated post
- Update request with blank text returns 400 validation error
- Update request with invalid URL returns 400 validation error
- Update request with invalid UUID path returns 400

**Technical details:**
- Add `PutMapping` import
- Add `UpdatePostRequest` import
- Returns 200 OK (not 201) for successful update

---

### Phase 5: Integration Tests

**Files**: None

**Test Files**: 
- `src/test/java/com/example/posts_service/UpdatePostIntegrationTest.java` (new)

Full stack integration tests with real database.

**Test scenarios (implement exactly these; no extra tests):**
- Successfully update own post returns 200 with all fields updated
- Update post with null attachment clears the attachment
- Update another user's post returns 403 Forbidden
- Update non-existent post returns 404 Not Found
- Update with invalid UUID returns 400 Bad Request
- Update without authorization header returns 401 Unauthorized
- createdAt timestamp remains unchanged after update
- updatedAt timestamp changes after update

**Technical details:**
- Follow `GetAllPostsIntegrationTest` pattern with `@BeforeEach` and `@AfterEach` cleanup
- Use `TestJwtUtil` to generate tokens for different users
- Use `@SpringBootTest` with `@AutoConfigureMockMvc`

---

## Technical Considerations

- **Dependencies**: No new dependencies required
- **Edge Cases**: 
  - Updating with same values (allowed, updatedAt still changes)
  - Empty string vs null for optional fields (both clear the field)
- **Testing Strategy**: Unit tests for service/controller, integration tests for full stack
- **Performance**: Single database read + write, no N+1 issues
- **Security**: Authorization check prevents cross-user modification

## Testing Notes

- Write tests alongside the production changes for each phase
- Run tests for each phase before moving to the next
- Integration tests require PostgreSQL running locally

## Success Criteria

- [ ] PUT /api/posts/{id} returns 200 with updated post for owner
- [ ] PUT /api/posts/{id} returns 403 when non-owner attempts update
- [ ] PUT /api/posts/{id} returns 404 for non-existent post
- [ ] PUT /api/posts/{id} returns 400 for invalid UUID format
- [ ] PUT /api/posts/{id} returns 400 for validation failures (blank text, invalid URL)
- [ ] PUT /api/posts/{id} returns 401 without auth header
- [ ] updatedAt changes on update, createdAt remains unchanged
- [ ] All existing tests continue to pass
