# Role-Based Authorization with Moderation Workflow Implementation Plan

## Overview

Implement role-based authorization with a content moderation workflow. Teachers create posts as DRAFT, moderators approve (→PUBLISHED) or reject (→REJECTED) them. Users can have multiple roles via a `user_roles` join table. Teachers can edit/delete their own DRAFT posts only.

## Architecture

```
Post Lifecycle:
  DRAFT → (moderator approves) → PUBLISHED
       → (moderator rejects)  → REJECTED
       → (user deletes)       → DELETED

Role-Based Access:
  TEACHER: Create posts (DRAFT), view own posts (all statuses), edit/delete own DRAFT posts
  MODERATOR: View all DRAFT posts, approve/reject posts
  
Endpoints:
  POST   /api/posts                    - Create post (DRAFT) [TEACHER]
  GET    /api/posts                    - List own posts [TEACHER] / List all DRAFT posts [MODERATOR]
  GET    /api/posts/{id}               - View post (own or PUBLISHED) [TEACHER] / Any post [MODERATOR]
  PUT    /api/posts/{id}               - Update own DRAFT post [TEACHER]
  DELETE /api/posts/{id}               - Delete own DRAFT post [TEACHER]
  POST   /api/posts/{id}/approve       - Approve post (DRAFT→PUBLISHED) [MODERATOR]
  POST   /api/posts/{id}/reject        - Reject post (DRAFT→REJECTED) [MODERATOR]
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**: 
- `src/main/java/com/example/posts_service/model/PostStatus.java`
- `src/main/java/com/example/posts_service/model/User.java`
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**: Existing tests

**What to do in this phase:**
- Run `./gradlew test --rerun` to verify all 86 tests pass
- Review current PostStatus enum (PUBLISHED, DELETED)
- Review current User entity and role field

**High-level characterization coverage:**
- Existing CRUD operations work correctly
- Existing authentication and authorization work correctly

---

### Phase 2: Database Schema - User Roles Table and PostStatus Update

**Files**: 
- `src/main/resources/db/migration/V5__create_user_roles_table.sql` (new)
- `src/main/resources/db/migration/V6__migrate_existing_roles.sql` (new)
- `src/main/resources/db/migration/V7__add_draft_rejected_status.sql` (new)
- `src/main/resources/db/migration/V8__seed_moderator_user.sql` (new)

**Test Files**: None (tested via integration in later phases)

Create user_roles join table, migrate existing role data, and add new post statuses.

**Key code changes:**

```sql
-- V5__create_user_roles_table.sql
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role);
```

```sql
-- V6__migrate_existing_roles.sql
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM users
ON CONFLICT DO NOTHING;

-- Keep role column for now, will be removed in future migration
```

```sql
-- V7__add_draft_rejected_status.sql
-- PostStatus enum now includes: DRAFT, PUBLISHED, REJECTED, DELETED
-- No DB changes needed as status is stored as VARCHAR
-- Update existing PUBLISHED posts to stay PUBLISHED (no change needed)
```

```sql
-- V8__seed_moderator_user.sql
INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at) VALUES
    ('33333333-3333-3333-3333-333333333333', 'moderator1', '$2a$10$...hash...', 'moderator1@school.edu', 'MODERATOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
    ('33333333-3333-3333-3333-333333333333', 'MODERATOR')
ON CONFLICT DO NOTHING;

-- Give teacher1 both TEACHER and MODERATOR roles for testing
INSERT INTO user_roles (user_id, role) VALUES
    ('11111111-1111-1111-1111-111111111111', 'MODERATOR')
ON CONFLICT DO NOTHING;
```

**Technical details:**
- Apply migrations manually via `psql`
- Generate BCrypt hash for moderator1 password

---

### Phase 3: Update User Entity and Repository for Multiple Roles

**Files**: 
- `src/main/java/com/example/posts_service/model/User.java`
- `src/main/java/com/example/posts_service/model/Role.java` (new enum)
- `src/main/java/com/example/posts_service/repository/UserRepository.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/repository/UserRepositoryTest.java`

Update User entity to support multiple roles via `@ElementCollection`.

**Key code changes:**

```java
// Role.java
public enum Role {
    TEACHER,
    MODERATOR
}
```

```java
// User.java - add roles collection
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role")
@Enumerated(EnumType.STRING)
private Set<Role> roles = new HashSet<>();

public boolean hasRole(Role role) {
    return roles.contains(role);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- User with single role returns correct role check
- User with multiple roles returns true for both roles

**Technical details:**
- Use `@ElementCollection` for simple role mapping
- Keep legacy `role` column temporarily for backward compatibility

---

### Phase 4: Update PostStatus Enum and Post Entity

**Files**: 
- `src/main/java/com/example/posts_service/model/PostStatus.java`

**Test Files**: None (tested via service/integration tests)

Add DRAFT and REJECTED statuses.

**Key code changes:**

```java
// PostStatus.java
public enum PostStatus {
    DRAFT,
    PUBLISHED,
    REJECTED,
    DELETED
}
```

**Technical details:**
- DRAFT: Initial status when teacher creates post
- PUBLISHED: After moderator approves
- REJECTED: After moderator rejects
- DELETED: When user soft-deletes their post

---

### Phase 5: Update Security - Add Role to UserPrincipal and JWT

**Files**: 
- `src/main/java/com/example/posts_service/security/UserPrincipal.java`
- `src/main/java/com/example/posts_service/security/JwtTokenProvider.java`
- `src/main/java/com/example/posts_service/security/JwtAuthenticationFilter.java`
- `src/main/java/com/example/posts_service/service/AuthService.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/security/JwtTokenProviderTest.java`

Include roles in JWT token and UserPrincipal.

**Key code changes:**

```java
// UserPrincipal.java - add roles
@Getter
@AllArgsConstructor
public class UserPrincipal {
    private UUID userId;
    private String username;
    private Set<Role> roles;

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
```

```java
// JwtTokenProvider.java - include roles in token
public String generateToken(UUID userId, String username, Set<Role> roles) {
    List<String> roleNames = roles.stream().map(Role::name).toList();
    return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("roles", roleNames)
            // ...
}

public Set<Role> getRolesFromToken(String token) {
    List<String> roleNames = parseClaims(token).get("roles", List.class);
    return roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Token with roles can be parsed to extract roles
- Generated token contains roles claim

**Technical details:**
- Store roles as list of strings in JWT
- Update AuthService to pass roles to generateToken
- Update JwtAuthenticationFilter to extract roles

---

### Phase 6: Update PostService for Role-Based Access

**Files**: 
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/repository/PostRepository.java`
- `src/main/java/com/example/posts_service/exception/InvalidPostStatusException.java` (new)

**Test Files**: 
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

Update service methods for role-based access and new workflow.

**Key code changes:**

```java
// PostRepository.java - add new queries
List<Post> findAllByCreatedByOrderByCreatedAtDesc(UUID createdBy);
List<Post> findAllByStatusOrderByCreatedAtDesc(PostStatus status);

// PostService.java
public PostResponse createPost(CreatePostRequest request, UUID userId) {
    // Create with DRAFT status instead of PUBLISHED
    Post post = new Post(..., PostStatus.DRAFT, ...);
}

public List<PostResponse> getAllPosts(UserPrincipal principal) {
    if (principal.hasRole(Role.MODERATOR)) {
        // Moderators see all DRAFT posts
        return postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.DRAFT)...;
    } else {
        // Teachers see their own posts (all statuses except DELETED)
        return postRepository.findAllByCreatedByOrderByCreatedAtDesc(principal.getUserId())
            .stream()
            .filter(p -> p.getStatus() != PostStatus.DELETED)
            ...;
    }
}

public PostResponse updatePost(UUID postId, UpdatePostRequest request, UserPrincipal principal) {
    // Only allow updating own DRAFT posts
    Post post = findPostForUser(postId, principal);
    if (post.getStatus() != PostStatus.DRAFT) {
        throw new InvalidPostStatusException("Can only edit DRAFT posts");
    }
    // ... update logic
}

public void deletePost(UUID postId, UserPrincipal principal) {
    // Only allow deleting own DRAFT posts
    Post post = findPostForUser(postId, principal);
    if (post.getStatus() != PostStatus.DRAFT) {
        throw new InvalidPostStatusException("Can only delete DRAFT posts");
    }
    // ... soft delete logic
}

public PostResponse approvePost(UUID postId, UserPrincipal principal) {
    // Moderator only
    if (!principal.hasRole(Role.MODERATOR)) {
        throw new UnauthorizedPostAccessException(postId);
    }
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));
    if (post.getStatus() != PostStatus.DRAFT) {
        throw new InvalidPostStatusException("Can only approve DRAFT posts");
    }
    post.setStatus(PostStatus.PUBLISHED);
    return toResponse(postRepository.save(post));
}

public PostResponse rejectPost(UUID postId, UserPrincipal principal) {
    // Similar to approvePost but sets REJECTED
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Create post sets status to DRAFT
- Teacher can update own DRAFT post
- Teacher cannot update non-DRAFT post (throws exception)
- Teacher cannot update another user's post
- Teacher can delete own DRAFT post
- Teacher cannot delete non-DRAFT post
- Moderator can approve DRAFT post (status becomes PUBLISHED)
- Moderator cannot approve non-DRAFT post
- Moderator can reject DRAFT post (status becomes REJECTED)
- getAllPosts returns own posts for teacher
- getAllPosts returns all DRAFT posts for moderator

**Technical details:**
- Add `InvalidPostStatusException` with 400 Bad Request handler

---

### Phase 7: Update PostController with New Endpoints

**Files**: 
- `src/main/java/com/example/posts_service/controller/PostController.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

Add approve/reject endpoints and update existing endpoints.

**Key code changes:**

```java
// PostController.java
@GetMapping
public ResponseEntity<List<PostResponse>> getAllPosts(@AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(postService.getAllPosts(principal));
}

@PostMapping("/{postId}/approve")
public ResponseEntity<PostResponse> approvePost(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(postService.approvePost(postId, principal));
}

@PostMapping("/{postId}/reject")
public ResponseEntity<PostResponse> rejectPost(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(postService.rejectPost(postId, principal));
}
```

```java
// GlobalExceptionHandler.java
@ExceptionHandler(InvalidPostStatusException.class)
public ResponseEntity<ErrorResponse> handleInvalidPostStatus(InvalidPostStatusException ex) {
    return ResponseEntity.badRequest()
            .body(new ErrorResponse(400, "Bad Request", ex.getMessage()));
}
```

**Test scenarios (implement exactly these; no extra tests):**
- POST /api/posts/{id}/approve returns 200 with PUBLISHED status
- POST /api/posts/{id}/reject returns 200 with REJECTED status
- Invalid UUID returns 400

**Technical details:**
- Pass UserPrincipal to service methods for role checking

---

### Phase 8: Update TestJwtUtil and Existing Tests

**Files**: 
- `src/test/java/com/example/posts_service/util/TestJwtUtil.java`

**Test Files**: 
- Update all existing tests to include roles in test tokens

Update test utility to generate tokens with roles.

**Key code changes:**

```java
// TestJwtUtil.java
public static String generateToken(UUID userId, String username) {
    return generateToken(userId, username, Set.of(Role.TEACHER));
}

public static String generateToken(UUID userId, String username, Set<Role> roles) {
    List<String> roleNames = roles.stream().map(Role::name).toList();
    return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("roles", roleNames)
            // ...
}

public static String generateModeratorToken(UUID userId, String username) {
    return generateToken(userId, username, Set.of(Role.MODERATOR));
}
```

**Technical details:**
- Default to TEACHER role for backward compatibility
- Add helper method for moderator token

---

### Phase 9: Integration Tests

**Files**: None

**Test Files**: 
- `src/test/java/com/example/posts_service/ModerationWorkflowIntegrationTest.java` (new)

Full stack integration tests for moderation workflow.

**Test scenarios (implement exactly these; no extra tests):**
- Teacher creates post in DRAFT status
- Teacher can view own DRAFT post
- Teacher can edit own DRAFT post
- Teacher can delete own DRAFT post
- Teacher cannot edit PUBLISHED post (returns 400)
- Teacher cannot delete PUBLISHED post (returns 400)
- Moderator can view all DRAFT posts
- Moderator can approve DRAFT post (becomes PUBLISHED)
- Moderator can reject DRAFT post (becomes REJECTED)
- Moderator cannot approve already PUBLISHED post (returns 400)
- Teacher without MODERATOR role cannot approve posts (returns 403)
- Login returns token with roles

**Technical details:**
- Use seeded users (teacher1, teacher2, moderator1)
- Verify JWT contains roles

---

## Technical Considerations

- **Dependencies**: No new dependencies required
- **Breaking Changes**: 
  - Posts now created as DRAFT instead of PUBLISHED
  - `GET /api/posts` behavior changes based on role
  - Existing PUBLISHED posts remain PUBLISHED
- **Edge Cases**:
  - User with both TEACHER and MODERATOR roles
  - Attempting to moderate own post
- **Testing Strategy**: Unit tests for service, controller tests, integration tests
- **Performance**: Role check is in-memory from JWT, no extra DB queries
- **Security**: Role validation at service layer, not just controller

## Migration Notes

1. Run migrations V5-V8 via psql
2. Existing posts remain PUBLISHED (no data migration needed)
3. Existing users get their role migrated to user_roles table
4. teacher1 gets both TEACHER and MODERATOR roles for testing

## Success Criteria

- [ ] Teachers create posts as DRAFT
- [ ] Teachers can only edit/delete their own DRAFT posts
- [ ] Moderators can view all DRAFT posts
- [ ] Moderators can approve posts (DRAFT→PUBLISHED)
- [ ] Moderators can reject posts (DRAFT→REJECTED)
- [ ] JWT tokens include roles
- [ ] GET /api/posts returns role-appropriate results
- [ ] All existing tests updated and passing
- [ ] New integration tests for moderation workflow
