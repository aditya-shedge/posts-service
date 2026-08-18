# User Story: Delete Post

## Business Context

Teachers may need to remove posts that are no longer relevant, contain outdated information, or were created in error. For example, a cancelled event announcement or a post with incorrect information should be removable. To maintain accountability and prevent unauthorized deletions, teachers can only delete posts they created.

---

## Story

**As a** teacher,  
**I want to** delete a post that I previously created,  
**So that** I can remove outdated or incorrect announcements from the system.

---

## Acceptance Criteria

### AC1: Successfully delete own post
**Given** I am an authenticated teacher  
**And** I have previously created a post  
**When** I submit a delete request for that post  
**Then** the post is permanently removed from the system  
**And** I receive a success confirmation  

### AC2: Cannot delete another teacher's post
**Given** I am an authenticated teacher  
**And** another teacher has created a post  
**When** I attempt to delete that post  
**Then** the deletion is rejected  
**And** I receive an "unauthorized" or "forbidden" error  
**And** the post remains in the system unchanged  

### AC3: Cannot delete non-existent post
**Given** I am an authenticated teacher  
**When** I attempt to delete a post that does not exist  
**Then** I receive a "post not found" error  

### AC4: Deleted post is no longer accessible
**Given** I am an authenticated teacher  
**And** I have successfully deleted my post  
**When** I or any other user attempts to view that post  
**Then** a "post not found" error is returned  
**And** the deleted post does not appear in the list of all posts  

### AC5: Cannot delete the same post twice
**Given** I am an authenticated teacher  
**And** I have already deleted a post  
**When** I attempt to delete the same post again  
**Then** I receive a "post not found" error  

---

## Out of Scope

- Soft delete (posts are permanently removed)
- Trash/recycle bin functionality
- Undo delete within a time window
- Bulk delete of multiple posts
- Admin ability to delete any post
- Confirmation prompt (handled by frontend)
- Archive functionality as an alternative to deletion

---

## Dependencies

- JWT Authentication must be implemented to identify the requesting user
- Post must exist in the database
- Authorization check must compare post's createdBy with authenticated user's ID

---

## Assumptions

- Deletion is permanent (hard delete, not soft delete)
- Only the original creator can delete a post (no admin override in this phase)
- No confirmation is required at the API level (frontend handles confirmation UX)
- Deleting a post does not affect any related data (no cascading deletes needed in this phase)

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
  "message": "You can only modify your own posts",
  "timestamp": "2026-08-18T15:00:00"
}
```

**Error Response - Post Not Found (404 Not Found):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-18T15:00:00"
}
```

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

### Error State - Unauthorized Delete Attempt

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  ❌ Cannot delete this post                           │ │
│  │     You can only delete posts that you created.      │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
