# User Story: Delete Post

## Business Context

Teachers may need to remove posts that are no longer relevant, contain outdated information, or were created in error. For example, a cancelled event announcement or a post with incorrect information should be removable. To maintain accountability and prevent unauthorized deletions, teachers can only delete posts they created. Using soft delete preserves data for potential recovery and audit purposes.

---

## Story

**As a** teacher,  
**I want to** delete a post that I previously created,  
**So that** I can remove outdated or incorrect announcements from view while preserving the data for recovery.

---

## Acceptance Criteria

### AC1: Successfully delete own post
**Given** I am an authenticated teacher  
**And** I have previously created a post  
**When** I submit a delete request for that post  
**Then** the post status is changed to DELETED  
**And** the updatedAt timestamp is set to the current time  
**And** I receive a success confirmation (204 No Content)  

### AC2: Cannot delete another teacher's post
**Given** I am an authenticated teacher  
**And** another teacher has created a post  
**When** I attempt to delete that post  
**Then** the deletion is rejected  
**And** I receive a "forbidden" error (403)  
**And** the post remains unchanged  

### AC3: Cannot delete non-existent post
**Given** I am an authenticated teacher  
**When** I attempt to delete a post that does not exist  
**Then** I receive a "post not found" error (404)  

### AC4: Deleted post is no longer visible
**Given** I am an authenticated teacher  
**And** I have successfully deleted my post  
**When** I or any other user attempts to view that post by ID  
**Then** a "post not found" error is returned (404)  
**And** the deleted post does not appear in the list of all posts  

### AC5: Cannot delete the same post twice
**Given** I am an authenticated teacher  
**And** I have already deleted a post  
**When** I attempt to delete the same post again  
**Then** I receive a "post not found" error (404)  

---

## Out of Scope

- Trash/recycle bin UI functionality
- Undo delete within a time window
- Bulk delete of multiple posts
- Admin ability to delete any post
- Confirmation prompt (handled by frontend)
- Archive functionality as an alternative to deletion
- API to restore deleted posts (future story)

---

## Dependencies

- JWT Authentication must be implemented to identify the requesting user
- Post must exist in the database with status PUBLISHED
- Authorization check must compare post's createdBy with authenticated user's ID
- PostStatus enum must include DELETED value

---

## Assumptions

- Deletion is soft delete (status changed to DELETED, data preserved)
- Only the original creator can delete a post (no admin override in this phase)
- No confirmation is required at the API level (frontend handles confirmation UX)
- Deleted posts are excluded from all GET queries (single post and list)
- Only PUBLISHED posts can be deleted (already-deleted posts return 404)

---

## API Specification

**Endpoint:** `DELETE /api/posts/{id}`

**Path Parameters:**
- `id` (UUID, required): The unique identifier of the post to delete

**Request Headers:**
```
Authorization: Bearer <jwt_token>
```

**Success Response (204 No Content):**
```
(empty response body)
```

**Error Response - Forbidden (403 Forbidden):**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You can only modify your own posts"
}
```

**Error Response - Post Not Found (404 Not Found):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Technical Notes

- Soft delete sets `status = PostStatus.DELETED`
- Repository queries must filter by `status = PUBLISHED`:
  - `findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED)`
  - `findByIdAndStatus(id, PostStatus.PUBLISHED)`
- The `@PreUpdate` callback will automatically update the `updatedAt` timestamp

---

## Mockups / Supporting Documents

### Delete Confirmation Dialog (Frontend)

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │                                                       │ │
│  │                    ⚠️ Delete Post?                    │ │
│  │                                                       │ │
│  │   Are you sure you want to delete this post?         │ │
│  │   This action cannot be undone.                      │ │
│  │                                                       │ │
│  │   ─────────────────────────────────────────────────   │ │
│  │                                                       │ │
│  │   "Class 5A will have a field trip to the Science   │ │
│  │   Museum on Friday..."                               │ │
│  │                                                       │ │
│  │   ─────────────────────────────────────────────────   │ │
│  │                                                       │ │
│  │              ┌────────┐  ┌────────────┐              │ │
│  │              │ Cancel │  │   Delete   │              │ │
│  │              └────────┘  └────────────┘              │ │
│  │                              (red)                   │ │
│  │                                                       │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Success State - Post Deleted

```
┌─────────────────────────────────────────────────────────────┐
│                        ALL POSTS                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  ✅ Post deleted successfully                         │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  (Remaining posts displayed below...)                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
