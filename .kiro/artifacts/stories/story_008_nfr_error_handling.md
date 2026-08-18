# NFR Story: Error Handling

## Business Context

When errors occur in the system, users and client applications need clear, consistent, and helpful error responses. Poor error handling can lead to confusion, wasted time troubleshooting, and a poor user experience. A well-designed error handling strategy ensures that errors are communicated clearly without exposing sensitive system internals, and that client applications can programmatically handle different error scenarios.

---

## Story

**As a** developer integrating with the Posts API,  
**I want** consistent and informative error responses for all failure scenarios,  
**So that** I can handle errors appropriately and provide meaningful feedback to users.

---

## Acceptance Criteria

### AC1: Post not found returns 404 with clear message
**Given** a user requests a post that does not exist  
**When** the server processes the request  
**Then** a 404 Not Found status is returned  
**And** the response body contains the error status, error type, descriptive message, and timestamp  
**And** the message indicates which post ID was not found  

### AC2: Unauthorized access returns 403 with clear message
**Given** an authenticated user attempts to update or delete another user's post  
**When** the server processes the request  
**Then** a 403 Forbidden status is returned  
**And** the response body contains the error status, error type, descriptive message, and timestamp  
**And** the message indicates the user can only modify their own posts  

### AC3: Authentication failure returns 401 with clear message
**Given** a user makes a request without valid authentication  
**When** the server processes the request  
**Then** a 401 Unauthorized status is returned  
**And** the response body contains the error status, error type, descriptive message, and timestamp  

### AC4: Validation errors return 400 with field-specific messages
**Given** a user submits a request with invalid input data  
**When** the server validates the request  
**Then** a 400 Bad Request status is returned  
**And** the response body contains the error status, error type, field-specific error messages, and timestamp  

### AC5: Unexpected errors return 500 without exposing internals
**Given** an unexpected server error occurs during request processing  
**When** the error is caught by the global exception handler  
**Then** a 500 Internal Server Error status is returned  
**And** the response body contains a generic error message  
**And** no stack traces, internal class names, or sensitive details are exposed  
**And** the error is logged on the server for debugging  

### AC6: Consistent error response structure
**Given** any error occurs in the system  
**When** an error response is returned  
**Then** the response follows a consistent JSON structure with: status (HTTP code), error (error type), message (human-readable description), and timestamp  

### AC7: Invalid JSON request body returns 400
**Given** a user submits a request with malformed JSON  
**When** the server attempts to parse the request  
**Then** a 400 Bad Request status is returned  
**And** the response indicates the request body is invalid  

### AC8: Unsupported HTTP method returns 405
**Given** a user makes a request using an unsupported HTTP method  
**When** the server processes the request  
**Then** a 405 Method Not Allowed status is returned  
**And** the response indicates which methods are supported  

---

## Out of Scope

- Custom error codes beyond HTTP status codes
- Error tracking integration (e.g., Sentry, Datadog)
- User-friendly error pages (API returns JSON only)
- Internationalized error messages
- Retry guidance in error responses
- Circuit breaker patterns

---

## Dependencies

- Global exception handler must be implemented with @RestControllerAdvice
- Custom domain exceptions must be created for business errors
- Logging framework must be configured for error logging

---

## Assumptions

- All error responses are in JSON format
- Timestamps use ISO-8601 format
- Error messages are in English
- Internal exceptions are logged at ERROR level before returning generic messages
- The error response structure is consistent across all endpoints

---

## Error Response Structure

```json
{
  "status": <HTTP status code>,
  "error": "<Error type>",
  "message": "<Human-readable description>",
  "timestamp": "<ISO-8601 timestamp>"
}
```

---

## Error Catalog

| Scenario | HTTP Status | Error Type | Example Message |
|----------|-------------|------------|-----------------|
| Post not found | 404 | Not Found | Post not found with id: 550e8400-... |
| Cannot modify others' post | 403 | Forbidden | You can only modify your own posts |
| Missing/invalid token | 401 | Unauthorized | Authentication required |
| Expired token | 401 | Unauthorized | Authentication token has expired |
| Validation error | 400 | Bad Request | text: Text is required |
| Invalid UUID format | 400 | Bad Request | Invalid post ID format |
| Malformed JSON | 400 | Bad Request | Invalid request body |
| Unsupported method | 405 | Method Not Allowed | Request method 'PATCH' is not supported |
| Server error | 500 | Internal Server Error | An unexpected error occurred |

---

## Error Response Examples

**404 Not Found:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-18T10:30:00"
}
```

**403 Forbidden:**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You can only modify your own posts",
  "timestamp": "2026-08-18T10:30:00"
}
```

**401 Unauthorized:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required. Please provide a valid token.",
  "timestamp": "2026-08-18T10:30:00"
}
```

**400 Bad Request (Validation):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "text: Text is required, attachment: Attachment must be a valid URL",
  "timestamp": "2026-08-18T10:30:00"
}
```

**500 Internal Server Error:**
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2026-08-18T10:30:00"
}
```

---

## Logging Requirements

For each error type, appropriate logging should occur:

| Error Type | Log Level | What to Log |
|------------|-----------|-------------|
| 400 Bad Request | WARN | Request details, validation failures |
| 401 Unauthorized | WARN | Request path, authentication failure reason |
| 403 Forbidden | WARN | User ID, resource ID, action attempted |
| 404 Not Found | INFO | Resource type and ID requested |
| 500 Server Error | ERROR | Full exception stack trace, request context |

**Example log entries:**

```
WARN  [PostController] Validation failed: text: Text is required
WARN  [JwtAuthFilter] Authentication failed: Token expired for request to POST /api/posts
WARN  [PostService] Unauthorized access: User 7c9e6679... attempted to delete post 550e8400... owned by 8d8e7779...
INFO  [PostService] Post not found: 550e8400-e29b-41d4-a716-446655440000
ERROR [GlobalExceptionHandler] Unexpected error processing request: POST /api/posts
java.lang.NullPointerException: ...
    at com.example.posts_service...
```

---

## Exception Hierarchy

```
RuntimeException
└── PostsServiceException (base class for all domain exceptions)
    ├── PostNotFoundException (404)
    └── UnauthorizedAccessException (403)
```

---

## Sequence Diagram - Error Handling Flow

```
┌──────┐       ┌──────────┐       ┌─────────┐       ┌─────────────────┐
│Client│       │Controller│       │ Service │       │ExceptionHandler │
└──┬───┘       └────┬─────┘       └────┬────┘       └────────┬────────┘
   │                │                  │                     │
   │ GET /posts/123 │                  │                     │
   │───────────────>│                  │                     │
   │                │ getPostById(123) │                     │
   │                │─────────────────>│                     │
   │                │                  │                     │
   │                │  PostNotFoundException                 │
   │                │<─────────────────│                     │
   │                │                  │                     │
   │                │────────────── Exception thrown ───────>│
   │                │                  │                     │
   │                │                  │    Build ErrorResponse
   │                │                  │    Log warning      │
   │                │<───────────── 404 Response ────────────│
   │                │                  │                     │
   │ 404 + JSON     │                  │                     │
   │<───────────────│                  │                     │
   │                │                  │                     │
```
