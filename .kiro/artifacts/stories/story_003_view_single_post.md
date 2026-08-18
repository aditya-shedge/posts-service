# User Story: View Single Post

## Business Context

When a teacher or parent wants to view the complete details of a specific post—perhaps after receiving a notification or a direct link—they need the ability to retrieve that individual post. This is essential for accessing specific announcements, following up on shared resources, or referencing a particular communication without scrolling through all posts.

---

## Story

**As a** teacher or parent,  
**I want to** view a specific post by its unique identifier,  
**So that** I can see the complete details of a particular announcement or update.

---

## Acceptance Criteria

### AC1: Successfully retrieve a post by ID
**Given** I am an authenticated user  
**And** a post exists with a specific ID  
**When** I request to view that post by its ID  
**Then** I receive the complete post details including ID, text, attachment, remarks, creator ID, creation timestamp, and last updated timestamp  

### AC2: Post not found returns appropriate error
**Given** I am an authenticated user  
**And** no post exists with the requested ID  
**When** I request to view a post with that non-existent ID  
**Then** I receive a "post not found" error  
**And** the error response includes a meaningful message  

### AC3: View any post regardless of creator
**Given** I am an authenticated user  
**And** a post was created by a different teacher  
**When** I request to view that post by its ID  
**Then** I can successfully view the post details  

### AC4: Invalid ID format returns appropriate error
**Given** I am an authenticated user  
**When** I request to view a post with an invalid ID format (not a valid UUID)  
**Then** I receive a bad request error  
**And** the error response indicates the ID format is invalid  

---

## Out of Scope

- View count or analytics tracking
- Related posts or suggestions
- Comments or replies on the post
- Post sharing functionality
- Bookmark or save post feature

---

## Dependencies

- JWT Authentication must be implemented to verify user identity
- Posts must exist in the database (depends on Create Post functionality)
- Post ID must be a valid UUID format

---

## Assumptions

- All authenticated users can view any post (no access restrictions based on creator)
- Post IDs are UUIDs and must be in valid UUID format
- Deleted posts cannot be viewed (404 returned)
- The full post content is returned (no partial or summary view)

---

## API Specification

**Endpoint:** `GET /api/posts/{id}`

**Path Parameters:**
- `id` (UUID, required): The unique identifier of the post

**Request Headers:**
```
Authorization: Bearer <jwt_token>
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Class 5A will have a field trip to the Science Museum on Friday. Please ensure permission slips are signed.",
  "attachment": "https://school-docs.example.com/permission-slip-2026.pdf",
  "remarks": "Parents can contact me for any questions regarding the trip.",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-18T10:30:00",
  "updatedAt": "2026-08-18T10:30:00"
}
```

**Error Response - Post Not Found (404 Not Found):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-18T10:35:00"
}
```

**Error Response - Invalid ID Format (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid post ID format",
  "timestamp": "2026-08-18T10:35:00"
}
```

---

## Mockups / Supporting Documents

```
┌─────────────────────────────────────────────────────────────┐
│  ← Back to All Posts                                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      POST DETAILS                           │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │  📅 Created: Aug 18, 2026 10:30 AM                  │   │
│  │  ✏️ Updated: Aug 18, 2026 10:30 AM                  │   │
│  │                                                     │   │
│  │  ─────────────────────────────────────────────────  │   │
│  │                                                     │   │
│  │  Class 5A will have a field trip to the Science    │   │
│  │  Museum on Friday. Please ensure permission slips  │   │
│  │  are signed.                                       │   │
│  │                                                     │   │
│  │  ─────────────────────────────────────────────────  │   │
│  │                                                     │   │
│  │  📎 Attachment:                                     │   │
│  │     permission-slip-2026.pdf                       │   │
│  │     [Open Link]                                    │   │
│  │                                                     │   │
│  │  ─────────────────────────────────────────────────  │   │
│  │                                                     │   │
│  │  💬 Remarks:                                        │   │
│  │     Parents can contact me for any questions       │   │
│  │     regarding the trip.                            │   │
│  │                                                     │   │
│  │  ─────────────────────────────────────────────────  │   │
│  │                                                     │   │
│  │  Posted by: Teacher ID 7c9e6679-7425-40de-944b...  │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Error State - Post Not Found

```
┌─────────────────────────────────────────────────────────────┐
│  ← Back to All Posts                                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                                                             │
│                    ┌─────────────┐                         │
│                    │     ⚠️      │                         │
│                    └─────────────┘                         │
│                                                             │
│                   Post Not Found                            │
│                                                             │
│     The post you're looking for doesn't exist or has       │
│     been removed.                                          │
│                                                             │
│                   [Go to All Posts]                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
