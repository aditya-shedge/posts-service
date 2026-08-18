# User Story: Update Post

## Business Context

Teachers occasionally need to correct or update information in their posts. A field trip date might change, an attachment URL might need updating, or additional remarks might need to be added. Allowing teachers to update their own posts ensures information stays accurate and current. To maintain accountability and prevent unauthorized modifications, teachers can only edit posts they created.

---

## Story

**As a** teacher,  
**I want to** update a post that I previously created,  
**So that** I can correct errors or add updated information to keep my announcements accurate.

---

## Acceptance Criteria

### AC1: Successfully update own post
**Given** I am an authenticated teacher  
**And** I have previously created a post  
**When** I submit an update request with modified text, attachment, and remarks  
**Then** the post is updated with the new values  
**And** the updatedAt timestamp is set to the current time  
**And** the createdAt timestamp remains unchanged  
**And** the createdBy field remains unchanged  

### AC2: Partial update - modify only specific fields
**Given** I am an authenticated teacher  
**And** I have previously created a post with text, attachment, and remarks  
**When** I submit an update request changing only the text  
**Then** the text is updated to the new value  
**And** I can set attachment or remarks to null/empty to remove them  

### AC3: Cannot update another teacher's post
**Given** I am an authenticated teacher  
**And** another teacher has created a post  
**When** I attempt to update that post  
**Then** the update is rejected  
**And** I receive an "unauthorized" or "forbidden" error  
**And** the original post remains unchanged  

### AC4: Cannot update non-existent post
**Given** I am an authenticated teacher  
**When** I attempt to update a post that does not exist  
**Then** I receive a "post not found" error  

### AC5: Updated post reflects changes immediately
**Given** I am an authenticated teacher  
**And** I have successfully updated my post  
**When** I or any other user views that post  
**Then** the updated content is displayed  

---

## Out of Scope

- Edit history or version tracking
- Undo/revert functionality
- Approval workflow for edits
- Notification to users who viewed the original post
- Bulk update of multiple posts
- Scheduled updates

---

## Dependencies

- JWT Authentication must be implemented to identify the requesting user
- Post must exist in the database
- Authorization check must compare post's createdBy with authenticated user's ID

---

## Assumptions

- Only the original creator can update a post (no admin override in this phase)
- All fields (text, attachment, remarks) can be updated in a single request
- The text field is required and cannot be set to blank during update
- Attachment and remarks can be set to null to clear them
- The update replaces the entire content (not a partial patch)

---

## API Specification

**Endpoint:** `PUT /api/posts/{id}`

**Path Parameters:**
- `id` (UUID, required): The unique identifier of the post to update

**Request Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "text": "UPDATED: Class 5A field trip to the Science Museum has been rescheduled to next Monday due to weather.",
  "attachment": "https://school-docs.example.com/updated-permission-slip-2026.pdf",
  "remarks": "New deadline for permission slips is Friday. Contact me with questions."
}
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "UPDATED: Class 5A field trip to the Science Museum has been rescheduled to next Monday due to weather.",
  "attachment": "https://school-docs.example.com/updated-permission-slip-2026.pdf",
  "remarks": "New deadline for permission slips is Friday. Contact me with questions.",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-18T10:30:00",
  "updatedAt": "2026-08-18T14:45:00"
}
```

**Error Response - Forbidden (403 Forbidden):**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You can only modify your own posts",
  "timestamp": "2026-08-18T14:45:00"
}
```

**Error Response - Post Not Found (404 Not Found):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-18T14:45:00"
}
```

---

## Mockups / Supporting Documents

```
┌─────────────────────────────────────────────────────────────┐
│  ← Back to Post                                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      EDIT POST                              │
│                                                             │
│  Post Content *                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ UPDATED: Class 5A field trip to the Science Museum │   │
│  │ has been rescheduled to next Monday due to weather.│   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Attachment URL (optional)                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ https://school-docs.example.com/updated-permission-│   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Remarks (optional)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ New deadline for permission slips is Friday.       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│                    ┌────────┐  ┌─────────────┐             │
│                    │ Cancel │  │ Save Changes│             │
│                    └────────┘  └─────────────┘             │
│                                                             │
│  Originally posted: Aug 18, 2026 10:30 AM                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Error State - Unauthorized Edit Attempt

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                    ┌─────────────┐                         │
│                    │     🚫      │                         │
│                    └─────────────┘                         │
│                                                             │
│                   Access Denied                             │
│                                                             │
│     You can only edit posts that you have created.         │
│                                                             │
│                   [Go to All Posts]                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
