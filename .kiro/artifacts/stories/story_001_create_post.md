# User Story: Create Post

## Business Context

Teachers in schools need a way to share class-related updates, announcements, and resources with other teachers and parents. Creating posts allows teachers to communicate important information such as class activities, homework assignments, event updates, and educational resources. Each post can include text content, an optional attachment URL (linking to documents, images, or external resources), and optional remarks for additional context.

---

## Story

**As a** teacher,  
**I want to** create a new post with text content, optional attachment, and optional remarks,  
**So that** I can share class-related updates and resources with other teachers and parents.

---

## Acceptance Criteria

### AC1: Successfully create a post with all fields
**Given** I am an authenticated teacher  
**And** I have valid post content to share  
**When** I submit a create post request with text, attachment URL, and remarks  
**Then** the post is created successfully  
**And** I receive a response with the created post details including a unique post ID  
**And** the post is attributed to me as the creator  
**And** the creation timestamp is recorded  

### AC2: Create a post with only required fields
**Given** I am an authenticated teacher  
**When** I submit a create post request with only the text content (no attachment or remarks)  
**Then** the post is created successfully  
**And** the attachment and remarks fields are stored as empty/null  

### AC3: Post ID is unique
**Given** I am an authenticated teacher  
**When** I create multiple posts  
**Then** each post receives a unique identifier  

### AC4: Timestamps are automatically set
**Given** I am an authenticated teacher  
**When** I create a new post  
**Then** the createdAt timestamp is automatically set to the current time  
**And** the updatedAt timestamp is automatically set to the current time  

---

## Out of Scope

- File upload functionality (only URLs are supported for attachments)
- Draft/publish workflow (posts are immediately visible upon creation)
- Post scheduling for future publication
- Rich text formatting or HTML content
- Post categories or tags
- Notifications to other users when a post is created

---

## Dependencies

- JWT Authentication must be implemented to identify the creating user
- Database schema for posts table must be in place
- User identity (UUID) must be available from the authentication token

---

## Assumptions

- Teachers are already authenticated through the school's identity system
- The authentication token contains the teacher's unique identifier (UUID)
- All authenticated users have permission to create posts
- Posts are visible to all authenticated users immediately upon creation
- Attachment URLs point to externally hosted resources (no file storage in this service)

---

## API Specification

**Endpoint:** `POST /api/posts`

**Request Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "text": "Class 5A will have a field trip to the Science Museum on Friday. Please ensure permission slips are signed.",
  "attachment": "https://school-docs.example.com/permission-slip-2026.pdf",
  "remarks": "Parents can contact me for any questions regarding the trip."
}
```

**Success Response (201 Created):**
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

---

## Mockups / Supporting Documents

```
┌─────────────────────────────────────────────────────────────┐
│                     CREATE NEW POST                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Post Content *                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Enter your post content here...                     │   │
│  │                                                     │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Attachment URL (optional)                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ https://                                            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Remarks (optional)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Additional context or instructions...               │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│                              ┌──────────────┐              │
│                              │  Create Post │              │
│                              └──────────────┘              │
└─────────────────────────────────────────────────────────────┘
```
