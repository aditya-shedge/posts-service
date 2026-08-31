# User Login Implementation Plan

## Overview

Implement `POST /api/auth/login` endpoint that authenticates users against a PostgreSQL `users` table and returns a JWT token. Users are seeded via Flyway migration with BCrypt-hashed passwords.

## Architecture

```
POST /api/auth/login → AuthController.login() → AuthService.authenticate()
    → UserRepository.findByUsername()
    → BCrypt password verification
    → JwtTokenProvider.generateToken()
    → Return JWT token in response
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**: 
- `src/main/java/com/example/posts_service/security/JwtTokenProvider.java`
- `src/main/java/com/example/posts_service/security/SecurityConfig.java`

**Test Files**:
- `src/test/java/com/example/posts_service/security/JwtTokenProviderTest.java`

**What to do in this phase:**
- Run `./gradlew test --rerun` to verify all 73 tests pass
- Confirm JwtTokenProvider can generate tokens (currently only validates)
- No code changes needed

**High-level characterization coverage:**
- Existing JWT validation works correctly
- Existing security configuration permits/denies correct endpoints

---

### Phase 2: Database Schema - Users Table

**Files**: 
- `src/main/resources/db/migration/V3__create_users_table.sql` (new)
- `src/main/resources/db/migration/V4__seed_test_users.sql` (new)

**Test Files**: None (tested via integration in Phase 6)

Create users table and seed test users with BCrypt-hashed passwords.

**Key code changes:**

```sql
-- V3__create_users_table.sql
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL DEFAULT 'TEACHER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
```

```sql
-- V4__seed_test_users.sql
-- Passwords are BCrypt hashed (password = "password123")
INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'teacher1', '$2a$10$...', 'teacher1@school.edu', 'TEACHER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'teacher2', '$2a$10$...', 'teacher2@school.edu', 'TEACHER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

**Technical details:**
- Generate BCrypt hash using Spring's `BCryptPasswordEncoder` for seed data
- Apply migration manually via `psql` (no Flyway Gradle plugin)

---

### Phase 3: User Entity and Repository

**Files**: 
- `src/main/java/com/example/posts_service/model/User.java` (new)
- `src/main/java/com/example/posts_service/repository/UserRepository.java` (new)

**Test Files**: 
- `src/test/java/com/example/posts_service/repository/UserRepositoryTest.java` (new)

Create User entity and repository.

**Key code changes:**

```java
// User.java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

```java
// UserRepository.java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Finding user by username returns the user when exists
- Finding user by username returns empty when not exists

**Technical details:**
- Use Lombok for boilerplate reduction
- Match existing entity patterns (Post.java)

---

### Phase 4: DTOs and Exception

**Files**: 
- `src/main/java/com/example/posts_service/dto/LoginRequest.java` (new)
- `src/main/java/com/example/posts_service/dto/LoginResponse.java` (new)
- `src/main/java/com/example/posts_service/exception/InvalidCredentialsException.java` (new)
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**: None (tested via integration in Phase 6)

Create login DTOs and exception handler for 401 on invalid credentials.

**Key code changes:**

```java
// LoginRequest.java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

```java
// LoginResponse.java
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private UUID userId;
    private String username;
}
```

```java
// InvalidCredentialsException.java
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
```

```java
// Add to GlobalExceptionHandler
@ExceptionHandler(InvalidCredentialsException.class)
public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
    log.warn("Login failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(401, "Unauthorized", ex.getMessage()));
}
```

**Technical details:**
- Use Lombok for DTOs
- Return 401 Unauthorized for invalid credentials (not 400)

---

### Phase 5: Auth Service and Token Generation

**Files**: 
- `src/main/java/com/example/posts_service/service/AuthService.java` (new)
- `src/main/java/com/example/posts_service/security/JwtTokenProvider.java`
- `src/main/java/com/example/posts_service/config/SecurityBeansConfig.java` (new)

**Test Files**: 
- `src/test/java/com/example/posts_service/service/AuthServiceTest.java` (new)

Create AuthService and add token generation to JwtTokenProvider.

**Key code changes:**

```java
// SecurityBeansConfig.java - separate config for PasswordEncoder bean
@Configuration
public class SecurityBeansConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

```java
// Add to JwtTokenProvider
public String generateToken(UUID userId, String username) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + 86400000); // 24 hours

    return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
}
```

```java
// AuthService.java
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return new LoginResponse(token, "Bearer", user.getId(), user.getUsername());
    }
}
```

**Test scenarios (implement exactly these; no extra tests):**
- Valid credentials returns LoginResponse with token
- Invalid username throws InvalidCredentialsException
- Invalid password throws InvalidCredentialsException

**Technical details:**
- PasswordEncoder bean in separate config to avoid circular dependency with SecurityConfig
- Token expiry: 24 hours (matches existing test token pattern)

---

### Phase 6: Auth Controller and Security Config

**Files**: 
- `src/main/java/com/example/posts_service/controller/AuthController.java` (new)
- `src/main/java/com/example/posts_service/security/SecurityConfig.java`

**Test Files**: 
- `src/test/java/com/example/posts_service/controller/AuthControllerTest.java` (new)

Create AuthController and permit `/api/auth/**` endpoints without authentication.

**Key code changes:**

```java
// AuthController.java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}
```

```java
// Update SecurityConfig - permit auth endpoints
.requestMatchers("/api/auth/**").permitAll()
```

**Test scenarios (implement exactly these; no extra tests):**
- Valid login returns 200 with token and user info
- Missing username returns 400 validation error
- Missing password returns 400 validation error

**Technical details:**
- `/api/auth/**` must be permitted without JWT
- Add before the authenticated matcher in security config

---

### Phase 7: Integration Tests

**Files**: None

**Test Files**: 
- `src/test/java/com/example/posts_service/LoginIntegrationTest.java` (new)

Full stack integration tests for login.

**Test scenarios (implement exactly these; no extra tests):**
- Valid credentials return 200 with JWT token
- Returned token can be used to access protected endpoints
- Invalid username returns 401 Unauthorized
- Invalid password returns 401 Unauthorized
- Missing credentials returns 400 Bad Request

**Technical details:**
- Seed test user in `@BeforeEach` or rely on Flyway migration
- Verify returned token works by calling GET /api/posts

---

## Technical Considerations

- **Dependencies**: No new dependencies (BCrypt included in spring-security)
- **Edge Cases**: 
  - Case sensitivity of username (keep case-sensitive for now)
  - Empty vs whitespace passwords (handled by @NotBlank)
- **Testing Strategy**: Unit tests for service, controller tests, integration tests
- **Performance**: Single database lookup per login
- **Security**: 
  - BCrypt with default strength (10 rounds)
  - Same error message for invalid username/password (prevents enumeration)
  - No password in logs

## Testing Notes

- Generate BCrypt hash for seed data: `new BCryptPasswordEncoder().encode("password123")`
- Apply migrations via psql before running integration tests

## Success Criteria

- [ ] POST /api/auth/login returns 200 with JWT token for valid credentials
- [ ] POST /api/auth/login returns 401 for invalid username
- [ ] POST /api/auth/login returns 401 for invalid password
- [ ] POST /api/auth/login returns 400 for missing credentials
- [ ] Returned JWT token works with existing protected endpoints
- [ ] All existing 73 tests continue to pass
