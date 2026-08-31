# Story 001: Create Post - Implementation Plan

## Overview

Implement the Create Post API endpoint that allows authenticated teachers to create new posts with text content, optional attachment URL, and optional remarks. This is the first API being built, so it includes foundational infrastructure: database schema, JWT authentication, validation, and error handling.

## Architecture

```
Client Request (POST /api/posts)
     ↓
[JWT Authentication Filter] → Validates token, extracts user ID
     ↓
[PostController] → Receives request, triggers validation, delegates to service
     ↓
[PostService] → Business logic, entity creation, timestamp handling
     ↓
[PostRepository] → Spring Data JPA persistence
     ↓
[PostgreSQL] → posts table (Flyway managed)
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
- Spring Boot auto-configuration is working correctly

---

### Phase 2: Database Schema and Entity Setup

**Files**:
- `src/main/resources/db/migration/V1__create_posts_table.sql`
- `src/main/java/com/example/posts_service/model/Post.java`
- `src/main/java/com/example/posts_service/repository/PostRepository.java`
- `src/main/resources/application.properties`

**Test Files**:
- `src/test/java/com/example/posts_service/repository/PostRepositoryTest.java`

**Description:**
Set up the database schema using Flyway migration and create the JPA entity and repository for the Post domain.

**Key code changes:**

```sql
-- V1__create_posts_table.sql (new file)
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
// new code - Post.java
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
    
    // Constructors, getters, setters, equals, hashCode
}
```

```java
// new code - PostRepository.java
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
}
```

```properties
# application.properties - update hibernate ddl-auto
spring.jpa.hibernate.ddl-auto=validate
```

**Test scenarios (implement exactly these; no extra tests):**

- Saving a post with all fields persists correctly and can be retrieved by ID
- Saving a post with null attachment and remarks persists correctly

**Technical details and Assumptions:**

- Use UUID for id (generated in service layer)
- Flyway runs migrations on startup
- Change `hibernate.ddl-auto` from `update` to `validate` to rely on Flyway
- Add H2 test dependency for repository tests

---

### Phase 3: JWT Security Configuration

**Files**:
- `build.gradle`
- `src/main/java/com/example/posts_service/security/JwtTokenProvider.java`
- `src/main/java/com/example/posts_service/security/JwtAuthenticationFilter.java`
- `src/main/java/com/example/posts_service/security/UserPrincipal.java`
- `src/main/java/com/example/posts_service/config/SecurityConfig.java`
- `src/main/resources/application.properties`

**Test Files**:
- `src/test/java/com/example/posts_service/security/JwtTokenProviderTest.java`

**Description:**
Implement JWT-based authentication with Spring Security. The filter extracts and validates JWT tokens, populating the SecurityContext with user details.

**Key code changes:**

```groovy
// build.gradle - add to dependencies
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

```java
// new code - UserPrincipal.java
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final String username;
    
    public UserPrincipal(UUID userId, String username) {
        this.userId = userId;
        this.username = username;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    // UserDetails method implementations
    // getAuthorities() returns empty list
    // getPassword() returns empty string
    // isAccountNonExpired/Locked/CredentialsNonExpired/Enabled return true
}
```

```java
// new code - JwtTokenProvider.java
@Component
public class JwtTokenProvider {
    
    @Value("${app.jwt.secret}")
    private String jwtSecret;
    
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return UUID.fromString(claims.getSubject());
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.get("username", String.class);
    }
    
    public boolean validateToken(String token) {
        // Parse and validate, return false on any exception
    }
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
```

```java
// new code - JwtAuthenticationFilter.java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        String token = extractTokenFromHeader(request);
        if (token != null && tokenProvider.validateToken(token)) {
            UUID userId = tokenProvider.getUserIdFromToken(token);
            String username = tokenProvider.getUsernameFromToken(token);
            UserPrincipal principal = new UserPrincipal(userId, username);
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
    
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

```java
// new code - SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                }))
            .build();
    }
}
```

```properties
# application.properties - add JWT config
app.jwt.secret=${JWT_SECRET:your-256-bit-secret-key-minimum-32-characters-long}
```

**Test scenarios (implement exactly these; no extra tests):**

- Valid JWT token returns correct user ID and username
- Expired JWT token fails validation
- Malformed JWT token fails validation
- Token with invalid signature fails validation

**Technical details and Assumptions:**

- JWT secret from environment variable with dev default
- CSRF disabled for stateless REST API
- Session management set to STATELESS
- All endpoints require authentication

---

### Phase 4: DTOs, Validation, and Error Handling

**Files**:
- `build.gradle`
- `src/main/java/com/example/posts_service/dto/CreatePostRequest.java`
- `src/main/java/com/example/posts_service/dto/PostResponse.java`
- `src/main/java/com/example/posts_service/dto/ErrorResponse.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**:
- `src/test/java/com/example/posts_service/dto/CreatePostRequestValidationTest.java`

**Description:**
Create request/response DTOs with Bean Validation and a global exception handler for consistent error responses.

**Key code changes:**

```groovy
// build.gradle - add validation dependency
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

```java
// new code - CreatePostRequest.java
public class CreatePostRequest {
    
    @NotBlank(message = "Text is required")
    private String text;
    
    @URL(message = "Attachment must be a valid URL")
    private String attachment;
    
    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
    
    // Default constructor, getters, setters
}
```

```java
// new code - PostResponse.java
public class PostResponse {
    private UUID id;
    private String text;
    private String attachment;
    private String remarks;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructor, getters, builder pattern or static factory
}
```

```java
// new code - ErrorResponse.java
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    
    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters
}
```

```java
// new code - GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("Validation failed: {}", message);
        ErrorResponse error = new ErrorResponse(400, "Bad Request", message);
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex) {
        log.warn("Invalid JSON: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(400, "Bad Request", "Invalid request body");
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse error = new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred");
        return ResponseEntity.status(500).body(error);
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**

- CreatePostRequest with blank text fails validation
- CreatePostRequest with invalid URL format for attachment fails validation
- CreatePostRequest with remarks exceeding 1000 characters fails validation
- CreatePostRequest with valid data passes validation
- CreatePostRequest with null attachment and remarks passes validation

**Technical details and Assumptions:**

- Use Hibernate Validator's `@URL` annotation (allows null)
- Error messages are concatenated with comma separator
- Timestamps use `LocalDateTime.now()`

---

### Phase 5: Service Layer Implementation

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

**Description:**
Implement the service layer with business logic for creating posts. The service generates UUIDs, sets timestamps, and maps between DTOs and entities.

**Key code changes:**

```java
// new code - PostService.java
@Service
public class PostService {
    
    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private final PostRepository postRepository;
    
    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }
    
    public PostResponse createPost(CreatePostRequest request, UUID userId) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setText(request.getText());
        post.setAttachment(request.getAttachment());
        post.setRemarks(request.getRemarks());
        post.setCreatedBy(userId);
        
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        
        Post saved = postRepository.save(post);
        log.info("Created post: id={}, createdBy={}", saved.getId(), userId);
        
        return toResponse(saved);
    }
    
    private PostResponse toResponse(Post post) {
        return new PostResponse(
            post.getId(),
            post.getText(),
            post.getAttachment(),
            post.getRemarks(),
            post.getCreatedBy(),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**

- Creating a post saves entity with generated UUID and returns response with all fields
- Creating a post sets createdAt and updatedAt to current time
- Creating a post with null attachment and remarks saves correctly
- Created post response contains the authenticated user's ID as createdBy

**Technical details and Assumptions:**

- Use constructor injection for PostRepository
- UUID generated using `UUID.randomUUID()`
- Timestamps use `LocalDateTime.now()`
- Logging at INFO level for successful creation

---

### Phase 6: Controller Layer and API Integration

**Files**:
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`
- `src/test/java/com/example/posts_service/CreatePostIntegrationTest.java`

**Description:**
Implement the REST controller for the Create Post endpoint. Controller extracts authenticated user from SecurityContext and delegates to service layer.

**Key code changes:**

```java
// new code - PostController.java
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
}
```

**Test scenarios (implement exactly these; no extra tests):**

**Unit tests (MockMvc with mocked service):**
- POST /api/posts with valid request returns 201 Created with post response
- POST /api/posts with missing text returns 400 Bad Request with validation error
- POST /api/posts with invalid attachment URL returns 400 Bad Request
- POST /api/posts with remarks exceeding 1000 characters returns 400 Bad Request
- POST /api/posts without Authorization header returns 401 Unauthorized

**Integration tests (full stack with test database):**
- Create post with all fields saves to database and returns correct response
- Create post with only required fields (text) saves successfully

**Technical details and Assumptions:**

- Use `@AuthenticationPrincipal` to extract UserPrincipal from SecurityContext
- Return 201 Created with the created resource in body
- Use `@Valid` to trigger Bean Validation on request body
- Integration tests use H2 in-memory database

---

## Technical Considerations

**Dependencies to add:**
- `spring-boot-starter-security` - Security framework
- `spring-boot-starter-validation` - Bean Validation
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.6) - JWT handling
- `com.h2database:h2` (test scope) - In-memory database for tests

**Edge Cases:**
- Empty attachment and remarks fields should be stored as null
- Very long text content is allowed (TEXT type in database)
- JWT token expiration is validated

**Testing Strategy:**
- Unit tests with Mockito for service layer isolation
- MockMvc tests for controller layer with mocked service
- Integration tests with H2 for full request flow
- Use test JWT tokens or mock SecurityContext in tests

**Performance:**
- Index on `created_by` for future ownership queries
- Index on `created_at` for ordering queries

**Security:**
- JWT secret externalized via environment variable
- Input validation on all request fields
- CSRF disabled for stateless API
- All endpoints require authentication

---

## Testing Notes

- Write tests alongside the production changes for each phase.
- Run tests for each phase before moving to the next.
- Use `@WebMvcTest` for controller unit tests with mocked services.
- Use `@DataJpaTest` with H2 for repository tests.
- Use `@SpringBootTest` with H2 for integration tests.
- Create a test utility to generate valid JWT tokens for testing.

---

## Success Criteria

- [ ] POST /api/posts endpoint creates a post and returns 201 Created
- [ ] Post is saved with generated UUID, timestamps, and createdBy from JWT
- [ ] Request without JWT token returns 401 Unauthorized
- [ ] Invalid/expired JWT token returns 401 Unauthorized
- [ ] Missing or blank text returns 400 Bad Request with validation error
- [ ] Invalid attachment URL returns 400 Bad Request with validation error
- [ ] Remarks exceeding 1000 characters returns 400 Bad Request
- [ ] Database schema is managed via Flyway migrations
- [ ] All test scenarios pass
- [ ] Application builds successfully with `./gradlew build`
