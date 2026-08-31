# Phase 1: CRUD APIs for Posts - Implementation Plan

## Overview

Build complete Post management functionality with CRUD APIs using Java 17, Spring Boot 4.1, Gradle, and PostgreSQL. Includes JWT-based authentication, ownership-based authorization, input validation, and custom exception handling following the layered architecture pattern: Controller → Service → Repository → PostgreSQL.

## Architecture

```
Client Request
     ↓
[JWT Authentication Filter] → Validates token, extracts user context
     ↓
[PostController] → REST endpoints, request/response mapping, validation
     ↓
[PostService] → Business logic, authorization checks, DTO mapping
     ↓
[PostRepository] → Spring Data JPA, database operations
     ↓
[PostgreSQL] → posts table with Flyway migrations
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers (including in test names and comments).
- Implement ONLY the test scenarios defined in the plan. Do not add extra tests unless the plan explicitly requires them.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**:
- `src/main/java/com/example/posts_service/PostsServiceApplication.java`

**Test Files**:
- `src/test/java/com/example/posts_service/PostsServiceApplicationTests.java`

**What to do in this phase:**

- Verify the existing Spring Boot application context loads successfully.
- Confirm the current test suite passes before adding new functionality.
- This is a greenfield project with minimal existing code, so characterization testing is limited to verifying the application bootstraps correctly.

**High-level characterization coverage:**

- Application context loads without errors
- Database connection configuration is valid (may require test containers or H2 for tests)

---

### Phase 2: Database Schema and Entity Setup

**Files**:
- `src/main/resources/db/migration/V1__create_posts_table.sql`
- `src/main/java/com/example/posts_service/model/Post.java`
- `src/main/java/com/example/posts_service/repository/PostRepository.java`

**Test Files**:
- `src/test/java/com/example/posts_service/repository/PostRepositoryTest.java`

**Description:**
Set up the database schema using Flyway migration and create the JPA entity and repository for the Post domain.

**Key code changes:**

```sql
-- V1__create_posts_table.sql
CREATE TABLE posts (
    id UUID PRIMARY KEY,
    text TEXT NOT NULL,
    attachment TEXT,
    remarks TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_posts_created_by ON posts(created_by);
CREATE INDEX idx_posts_created_at ON posts(created_at);
```

```java
// new code
// Post.java
@Entity
@Table(name = "posts")
public class Post {
    @Id
    private UUID id;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
    
    @Column(columnDefinition = "TEXT")
    private String attachment;
    
    @Column(columnDefinition = "TEXT")
    private String remarks;
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Getters, setters, equals, hashCode
}
```

```java
// new code
// PostRepository.java
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByOrderByCreatedAtDesc();
}
```

**Test scenarios (implement exactly these; no extra tests):**

- Saving a post with all fields persists correctly and can be retrieved by ID
- Finding all posts returns them ordered by creation date descending
- Updating a post modifies the updatedAt timestamp

**Technical details and Assumptions:**

- Use UUID for id generation (client-provided or service-generated)
- Flyway will auto-run migrations on startup
- Index on `created_by` for future authorization queries
- Index on `created_at` for ordering efficiency
- Update `application.properties` to disable `hibernate.ddl-auto` in favour of Flyway

---

### Phase 3: Security Configuration and JWT Authentication

**Files**:
- `src/main/java/com/example/posts_service/config/SecurityConfig.java`
- `src/main/java/com/example/posts_service/security/JwtAuthenticationFilter.java`
- `src/main/java/com/example/posts_service/security/JwtTokenProvider.java`
- `src/main/java/com/example/posts_service/security/UserPrincipal.java`
- `src/main/resources/application.properties`
- `build.gradle`

**Test Files**:
- `src/test/java/com/example/posts_service/security/JwtTokenProviderTest.java`
- `src/test/java/com/example/posts_service/security/JwtAuthenticationFilterTest.java`

**Description:**
Implement JWT-based authentication with Spring Security. The filter extracts and validates JWT tokens, populating the SecurityContext with user details including the user's UUID.

**Key code changes:**

```groovy
// build.gradle - add dependencies
// existing code
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    // ...
}

// new code
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    // ...
}
```

```java
// new code
// UserPrincipal.java
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final String username;
    
    // Constructor, getters, UserDetails method implementations
}
```

```java
// new code
// JwtTokenProvider.java
@Component
public class JwtTokenProvider {
    
    @Value("${app.jwt.secret}")
    private String jwtSecret;
    
    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;
    
    public String generateToken(UUID userId, String username) { /* ... */ }
    
    public UUID getUserIdFromToken(String token) { /* ... */ }
    
    public boolean validateToken(String token) { /* ... */ }
}
```

```java
// new code
// JwtAuthenticationFilter.java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        // Extract token from Authorization header
        // Validate token
        // Set SecurityContext with UserPrincipal
    }
}
```

```java
// new code
// SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

```properties
# application.properties additions
app.jwt.secret=${JWT_SECRET:your-256-bit-secret-key-for-development-only}
app.jwt.expiration-ms=86400000
```

**Test scenarios (implement exactly these; no extra tests):**

- Valid JWT token is parsed and returns correct user ID
- Expired JWT token fails validation
- Malformed JWT token fails validation
- Request without Authorization header is rejected with 401
- Request with valid token proceeds to controller

**Technical details and Assumptions:**

- JWT secret should be externalized via environment variable in production
- Token contains userId (UUID) as subject and username as claim
- CSRF disabled for stateless REST API
- Session management set to STATELESS

---

### Phase 4: DTOs, Validation, and Exception Handling

**Files**:
- `src/main/java/com/example/posts_service/dto/CreatePostRequest.java`
- `src/main/java/com/example/posts_service/dto/UpdatePostRequest.java`
- `src/main/java/com/example/posts_service/dto/PostResponse.java`
- `src/main/java/com/example/posts_service/exception/PostNotFoundException.java`
- `src/main/java/com/example/posts_service/exception/UnauthorizedAccessException.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`
- `src/main/java/com/example/posts_service/dto/ErrorResponse.java`
- `build.gradle`

**Test Files**:
- `src/test/java/com/example/posts_service/exception/GlobalExceptionHandlerTest.java`

**Description:**
Create request/response DTOs with Bean Validation, custom domain exceptions, and a global exception handler that returns consistent error responses.

**Key code changes:**

```groovy
// build.gradle - add validation dependency
// new code (add to dependencies)
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

```java
// new code
// CreatePostRequest.java
public class CreatePostRequest {
    
    @NotBlank(message = "Text is required")
    private String text;
    
    @URL(message = "Attachment must be a valid URL")
    private String attachment;
    
    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
    
    // Getters, setters
}
```

```java
// new code
// UpdatePostRequest.java
public class UpdatePostRequest {
    
    @NotBlank(message = "Text is required")
    private String text;
    
    @URL(message = "Attachment must be a valid URL")
    private String attachment;
    
    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
    
    // Getters, setters
}
```

```java
// new code
// PostResponse.java
public class PostResponse {
    private UUID id;
    private String text;
    private String attachment;
    private String remarks;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructor, getters, builder pattern
}
```

```java
// new code
// ErrorResponse.java
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    
    // Constructor, getters
}
```

```java
// new code
// PostNotFoundException.java
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(UUID postId) {
        super(String.format("Post not found with id: %s", postId));
    }
}
```

```java
// new code
// UnauthorizedAccessException.java
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
```

```java
// new code
// GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
        // Return 404 with error response
    }
    
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        // Return 403 with error response
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        // Return 400 with validation error details
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        // Return 500 with generic error message (don't expose internal details)
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**

- PostNotFoundException returns 404 status with error code and message
- UnauthorizedAccessException returns 403 status with error code and message
- Validation errors return 400 status with field-specific error messages
- Generic exceptions return 500 status without exposing internal details

**Technical details and Assumptions:**

- Use `@URL` from Hibernate Validator for attachment validation (allows null)
- Error response includes timestamp for debugging/correlation
- Do not expose stack traces or internal details in production error responses
- Log exceptions at appropriate levels in the handler

---

### Phase 5: Service Layer Implementation

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/service/PostServiceImpl.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceImplTest.java`

**Description:**
Implement the business logic layer with ownership-based authorization. Users can read all posts but can only update/delete their own posts (matching `createdBy`).

**Key code changes:**

```java
// new code
// PostService.java
public interface PostService {
    PostResponse createPost(CreatePostRequest request, UUID userId);
    PostResponse getPostById(UUID postId);
    List<PostResponse> getAllPosts();
    PostResponse updatePost(UUID postId, UpdatePostRequest request, UUID userId);
    void deletePost(UUID postId, UUID userId);
}
```

```java
// new code
// PostServiceImpl.java
@Service
public class PostServiceImpl implements PostService {
    
    private final PostRepository postRepository;
    
    public PostServiceImpl(PostRepository postRepository) {
        this.postRepository = postRepository;
    }
    
    @Override
    public PostResponse createPost(CreatePostRequest request, UUID userId) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setText(request.getText());
        post.setAttachment(request.getAttachment());
        post.setRemarks(request.getRemarks());
        post.setCreatedBy(userId);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        
        Post saved = postRepository.save(post);
        return toResponse(saved);
    }
    
    @Override
    public PostResponse getPostById(UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
        return toResponse(post);
    }
    
    @Override
    public List<PostResponse> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }
    
    @Override
    public PostResponse updatePost(UUID postId, UpdatePostRequest request, UUID userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
        
        verifyOwnership(post, userId);
        
        post.setText(request.getText());
        post.setAttachment(request.getAttachment());
        post.setRemarks(request.getRemarks());
        post.setUpdatedAt(LocalDateTime.now());
        
        Post updated = postRepository.save(post);
        return toResponse(updated);
    }
    
    @Override
    public void deletePost(UUID postId, UUID userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException(postId));
        
        verifyOwnership(post, userId);
        
        postRepository.delete(post);
    }
    
    private void verifyOwnership(Post post, UUID userId) {
        if (!post.getCreatedBy().equals(userId)) {
            throw new UnauthorizedAccessException("You can only modify your own posts");
        }
    }
    
    private PostResponse toResponse(Post post) {
        // Map entity to DTO
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**

- Creating a post saves with provided fields and generated ID, createdAt, updatedAt
- Getting a post by ID returns the correct post response
- Getting a non-existent post throws PostNotFoundException
- Getting all posts returns posts ordered by creation date descending
- Updating own post modifies text, attachment, remarks, and updatedAt
- Updating another user's post throws UnauthorizedAccessException
- Deleting own post removes it from the database
- Deleting another user's post throws UnauthorizedAccessException

**Technical details and Assumptions:**

- Use constructor injection for dependencies
- UUID.randomUUID() for post ID generation
- LocalDateTime.now() for timestamps (consider Clock injection for testability)
- Ownership check compares post.createdBy with authenticated user's ID

---

### Phase 6: Controller Layer and API Integration

**Files**:
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`
- `src/test/java/com/example/posts_service/PostsApiIntegrationTest.java`

**Description:**
Implement the REST controller with all five CRUD endpoints. Controller extracts authenticated user from SecurityContext and delegates to service layer.

**Key code changes:**

```java
// new code
// PostController.java
@RestController
@RequestMapping("/api/posts")
public class PostController {
    
    private final PostService postService;
    
    public PostController(PostService postService) {
        this.postService = postService;
    }
    
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.createPost(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        List<PostResponse> posts = postService.getAllPosts();
        return ResponseEntity.ok(posts);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable UUID id) {
        PostResponse response = postService.getPostById(id);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.updatePost(id, request, principal.getUserId());
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        postService.deletePost(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**

**Unit tests (MockMvc with mocked service):**
- POST /api/posts with valid request returns 201 Created with post response
- POST /api/posts with missing text returns 400 Bad Request with validation error
- POST /api/posts with invalid attachment URL returns 400 Bad Request
- GET /api/posts returns 200 OK with list of posts
- GET /api/posts/{id} with valid ID returns 200 OK with post response
- GET /api/posts/{id} with non-existent ID returns 404 Not Found
- PUT /api/posts/{id} with valid request returns 200 OK with updated post
- PUT /api/posts/{id} for another user's post returns 403 Forbidden
- DELETE /api/posts/{id} for own post returns 204 No Content
- DELETE /api/posts/{id} for another user's post returns 403 Forbidden
- Request without Authorization header returns 401 Unauthorized

**Integration tests (full stack with test database):**
- Full CRUD flow: create, read, update, delete a post
- Authorization enforcement: user cannot modify another user's post

**Technical details and Assumptions:**

- Use `@AuthenticationPrincipal` to extract UserPrincipal from SecurityContext
- Return 201 Created for POST with the created resource in body
- Return 204 No Content for DELETE (no body)
- Use `@Valid` to trigger Bean Validation on request bodies
- Integration tests should use Testcontainers for PostgreSQL or H2 in-memory database

---

## Technical Considerations

**Dependencies to add:**
- `spring-boot-starter-security` - Security framework
- `spring-boot-starter-validation` - Bean Validation
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.6) - JWT handling
- `testcontainers` (optional) - For integration tests with real PostgreSQL

**Edge Cases:**
- Empty attachment and remarks fields should be allowed (nullable)
- UUID path variable parsing errors should return 400 Bad Request
- Concurrent updates to same post (last write wins with current implementation)
- Very long text content (TEXT type handles this, but consider practical limits)

**Testing Strategy:**
- Unit tests with Mockito for service layer isolation
- MockMvc tests for controller layer with mocked service
- Integration tests for full request flow with test database
- Tests should use a test JWT or mock the security context

**Performance:**
- Indexes on `created_by` and `created_at` for efficient queries
- No pagination (as specified), but consider adding if data volume grows
- Connection pooling via HikariCP (Spring Boot default)

**Security:**
- JWT secret must be externalized in production
- Input validation on all request fields
- Ownership verification before modification/deletion
- No sensitive data in error responses
- CSRF disabled (stateless API with JWT)

---

## Testing Notes

- Write tests alongside the production changes for each phase.
- Run tests for each phase before moving to the next.
- Use `@WebMvcTest` for controller unit tests with mocked services.
- Use `@DataJpaTest` for repository tests with embedded database.
- Use `@SpringBootTest` with Testcontainers or H2 for integration tests.
- Mock `JwtTokenProvider` in controller tests or use a test token generator.

---

## Success Criteria

- [ ] All five CRUD endpoints (POST, GET all, GET by ID, PUT, DELETE) are functional
- [ ] JWT authentication rejects unauthenticated requests with 401
- [ ] Users can only update/delete their own posts (403 for others' posts)
- [ ] Validation errors return 400 with descriptive field-level messages
- [ ] Post not found returns 404 with error response
- [ ] Database schema is managed via Flyway migrations
- [ ] All test scenarios pass
- [ ] Application builds successfully with `./gradlew build`
- [ ] API can be tested manually with tools like Postman/curl
