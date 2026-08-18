# NFR Story: API Security - JWT Authentication

## Business Context

The Posts service handles school communications that should only be accessible to authenticated users (teachers, parents, and staff). Unauthorized access to posts could expose sensitive school information or allow malicious actors to create fraudulent announcements. JWT (JSON Web Token) based authentication provides a stateless, scalable security mechanism that integrates well with the school's existing identity management system.

---

## Story

**As a** system administrator,  
**I want** all API endpoints to require valid JWT authentication,  
**So that** only authorized school personnel can access and manage posts.

---

## Acceptance Criteria

### AC1: Unauthenticated requests are rejected
**Given** a user makes a request to any /api/posts endpoint  
**And** the request does not include an Authorization header  
**When** the server processes the request  
**Then** the request is rejected with a 401 Unauthorized status  
**And** an error response is returned indicating authentication is required  

### AC2: Invalid token is rejected
**Given** a user makes a request with an Authorization header  
**And** the token is malformed or has an invalid signature  
**When** the server processes the request  
**Then** the request is rejected with a 401 Unauthorized status  
**And** an error response is returned indicating the token is invalid  

### AC3: Expired token is rejected
**Given** a user makes a request with an Authorization header  
**And** the token has expired (past its expiration time)  
**When** the server processes the request  
**Then** the request is rejected with a 401 Unauthorized status  
**And** an error response is returned indicating the token has expired  

### AC4: Valid token grants access
**Given** a user makes a request with an Authorization header  
**And** the token is valid, properly signed, and not expired  
**When** the server processes the request  
**Then** the request proceeds to the appropriate endpoint handler  
**And** the user's identity (UUID) is extracted from the token and made available to the application  

### AC5: Token contains required user information
**Given** a valid JWT token  
**Then** the token contains the user's unique identifier (UUID) as the subject  
**And** the token contains an expiration timestamp  
**And** the token is signed with the configured secret key  

### AC6: Authorization header format is validated
**Given** a user makes a request with an Authorization header  
**And** the header value does not follow the "Bearer <token>" format  
**When** the server processes the request  
**Then** the request is rejected with a 401 Unauthorized status  

---

## Out of Scope

- User registration and login endpoints (handled by external identity service)
- Token refresh mechanism
- Role-based access control (RBAC) beyond ownership checks
- OAuth2/OpenID Connect integration
- Multi-factor authentication
- Session management (API is stateless)
- Token revocation/blacklisting

---

## Dependencies

- JWT secret key must be configured via environment variable
- Token expiration duration must be configurable
- Spring Security framework must be integrated

---

## Assumptions

- Tokens are issued by an external authentication service (school's identity system)
- The Posts service only validates tokens, it does not issue them
- All users with valid tokens have basic access to view posts
- Ownership-based authorization (who can edit/delete) is handled separately in the application layer
- The JWT secret is shared between the identity service and this service

---

## Technical Specifications

### JWT Token Structure

**Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload:**
```json
{
  "sub": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "username": "john.teacher",
  "iat": 1723962600,
  "exp": 1724049000
}
```

### Configuration Properties

```properties
# JWT Configuration
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=86400000
```

### Error Response Format

**401 Unauthorized - Missing Token:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required. Please provide a valid token.",
  "timestamp": "2026-08-18T10:00:00"
}
```

**401 Unauthorized - Invalid Token:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid authentication token.",
  "timestamp": "2026-08-18T10:00:00"
}
```

**401 Unauthorized - Expired Token:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication token has expired.",
  "timestamp": "2026-08-18T10:00:00"
}
```

---

## Security Considerations

1. **Secret Key Management**: JWT secret must never be hardcoded; use environment variables
2. **HTTPS Only**: In production, all API traffic must be over HTTPS to prevent token interception
3. **Token Expiration**: Tokens should have a reasonable expiration time (24 hours default)
4. **No Sensitive Data in Token**: Token payload should only contain user ID and minimal claims
5. **Signature Validation**: Always validate token signature before trusting any claims
6. **Timing Attacks**: Use constant-time comparison for signature validation

---

## Sequence Diagram

```
┌──────┐          ┌─────────────┐          ┌───────────────┐          ┌─────────────┐
│Client│          │JWT Filter   │          │Token Provider │          │Controller   │
└──┬───┘          └──────┬──────┘          └───────┬───────┘          └──────┬──────┘
   │                     │                         │                         │
   │  Request + Token    │                         │                         │
   │────────────────────>│                         │                         │
   │                     │                         │                         │
   │                     │  Extract & Validate     │                         │
   │                     │────────────────────────>│                         │
   │                     │                         │                         │
   │                     │  Valid + User ID        │                         │
   │                     │<────────────────────────│                         │
   │                     │                         │                         │
   │                     │  Set Security Context   │                         │
   │                     │─────────────────────────────────────────────────>│
   │                     │                         │                         │
   │                     │                         │      Process Request    │
   │                     │                         │<────────────────────────│
   │                     │                         │                         │
   │  Response           │                         │                         │
   │<────────────────────────────────────────────────────────────────────────│
   │                     │                         │                         │
```

```
┌──────┐          ┌─────────────┐          ┌───────────────┐
│Client│          │JWT Filter   │          │Token Provider │
└──┬───┘          └──────┬──────┘          └───────┬───────┘
   │                     │                         │
   │  Request (No Token) │                         │
   │────────────────────>│                         │
   │                     │                         │
   │  401 Unauthorized   │                         │
   │<────────────────────│                         │
   │                     │                         │
```
