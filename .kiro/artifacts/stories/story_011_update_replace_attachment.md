# User Story: Update or Replace Attachment on DRAFT Post

## Business Context

Teachers may need to change the attachment on a post after creation — perhaps they uploaded the wrong file, have an updated version, or want to remove the attachment entirely. Since posts start as DRAFTs and must be approved before publishing, teachers should be able to modify attachments while the post is still in DRAFT status.

When an attachment is replaced, the old file should be deleted from Cloudinary to avoid accumulating unused files and consuming storage.

---

## Story

**As a** teacher,  
**I want to** update or replace the attachment on my DRAFT post,  
**So that** I can correct mistakes or provide updated files before the post is published.

---

## Acceptance Criteria

### AC1: Replace attachment with a new file
**Given** I am an authenticated teacher  
**And** I have a DRAFT post with an existing attachment  
**When** I submit an update request with a new attachment file  
**Then** the new file is uploaded to Cloudinary  
**And** the old file is deleted from Cloudinary  
**And** the post's attachment URL is updated to the new file  
**And** the `attachmentFilename` is updated  
**And** the `attachmentStatus` is set to `UPLOADED`  

### AC2: Remove attachment without replacement
**Given** I am an authenticated teacher  
**And** I have a DRAFT post with an existing attachment  
**When** I submit an update request with `removeAttachment: true` and no new file  
**Then** the existing file is deleted from Cloudinary  
**And** the post's `attachment` field is set to null  
**And** the `attachmentFilename` is set to null  
**And** the `attachmentStatus` is set to null  

### AC3: Add attachment to post that had none
**Given** I am an authenticated teacher  
**And** I have a DRAFT post without an attachment  
**When** I submit an update request with an attachment file  
**Then** the file is uploaded to Cloudinary  
**And** the post's attachment URL is set  
**And** the `attachmentStatus` is set to `UPLOADED`  

### AC4: Cannot modify attachment on non-DRAFT posts
**Given** I am an authenticated teacher  
**And** I have a post with status PUBLISHED, REJECTED, or DELETED  
**When** I attempt to update the attachment  
**Then** the request is rejected with 400 Bad Request  
**And** the error message indicates only DRAFT posts can be modified  

### AC5: File validation applies to updates
**Given** I am an authenticated teacher  
**When** I attempt to upload an invalid file type or file exceeding 5 MB  
**Then** the request is rejected with 400 Bad Request  
**And** the existing attachment is unchanged  

### AC6: Cloudinary deletion failure does not block update
**Given** I am an authenticated teacher  
**And** I am replacing an existing attachment  
**When** the new file uploads successfully but old file deletion fails  
**Then** the post is updated with the new attachment  
**And** the old file deletion failure is logged for cleanup  

### AC7: Update text/remarks without affecting attachment
**Given** I am an authenticated teacher  
**And** I have a DRAFT post with an attachment  
**When** I submit an update request with only text/remarks changes (no file, no removeAttachment)  
**Then** the text/remarks are updated  
**And** the attachment remains unchanged  

### AC8: Replace failed attachment
**Given** I am an authenticated teacher  
**And** I have a DRAFT post with `attachmentStatus: FAILED`  
**When** I submit an update request with a new attachment file  
**Then** the new file is uploaded to Cloudinary  
**And** the `attachmentStatus` is updated to `UPLOADED`  
**And** any pending retry jobs for this post are cancelled  

---

## Out of Scope

- Attachment versioning/history
- Batch updating multiple posts
- Moderator ability to modify attachments

---

## Dependencies

- Story 009 (Upload Attachment When Creating Post) must be implemented
- Story 010 (Background Retry) for cancelling pending retries
- Cloudinary delete API integration

---

## Assumptions

- The `attachmentPublicId` stored during upload is used to delete files from Cloudinary
- Cloudinary deletion is idempotent (deleting non-existent file doesn't error)
- Teachers can only modify their own posts (existing authorization)

---

## Technical Notes

### Request Format
The Update Post endpoint accepts `multipart/form-data`:
- `text` — optional, string (updates text if provided)
- `remarks` — optional, string (updates remarks if provided)
- `attachment` — optional, file (replaces attachment if provided)
- `removeAttachment` — optional, boolean (removes attachment if true)

### Cloudinary Deletion
```java
cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
```

### Handling Pending Uploads
If the post has `attachmentStatus: PENDING` (upload in progress):
- Cancel the pending retry by clearing `attachment_temp_path` and `attachment_next_retry_at`
- Delete the temporary file
- Proceed with the new upload

---

## API Specification

**Endpoint:** `PUT /api/posts/{id}`

**Request Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data
```

**Request Body - Replace Attachment:**
```
text: "Updated field trip announcement"
attachment: [file binary - updated-permission-slip.pdf]
```

**Request Body - Remove Attachment:**
```
text: "Updated field trip announcement"
removeAttachment: true
```

**Request Body - Update Text Only:**
```
text: "Updated field trip announcement"
remarks: "New remarks"
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Updated field trip announcement",
  "attachment": "https://res.cloudinary.com/school/image/upload/v1234/posts/xyz789.pdf",
  "attachmentFilename": "updated-permission-slip.pdf",
  "attachmentStatus": "UPLOADED",
  "remarks": "New remarks",
  "status": "DRAFT",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-21T10:30:00",
  "updatedAt": "2026-08-21T11:45:00"
}
```

**Success Response - Attachment Removed (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Updated field trip announcement",
  "attachment": null,
  "attachmentFilename": null,
  "attachmentStatus": null,
  "remarks": "New remarks",
  "status": "DRAFT",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-21T10:30:00",
  "updatedAt": "2026-08-21T11:45:00"
}
```

---

## Sequence Diagram - Replace Attachment

```
┌────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│Teacher │     │ Controller │     │  Service   │     │ Cloudinary │     │ PostgreSQL │
└───┬────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘
    │                │                  │                  │                  │
    │ PUT /api/posts/{id}               │                  │                  │
    │ (multipart)    │                  │                  │                  │
    │───────────────>│                  │                  │                  │
    │                │                  │                  │                  │
    │                │ updatePost()     │                  │                  │
    │                │─────────────────>│                  │                  │
    │                │                  │                  │                  │
    │                │                  │ findById()       │                  │
    │                │                  │─────────────────────────────────────>
    │                │                  │                  │                  │
    │                │                  │ post             │                  │
    │                │                  │<─────────────────────────────────────
    │                │                  │                  │                  │
    │                │                  │ upload(newFile)  │                  │
    │                │                  │─────────────────>│                  │
    │                │                  │                  │                  │
    │                │                  │ {url, publicId}  │                  │
    │                │                  │<─────────────────│                  │
    │                │                  │                  │                  │
    │                │                  │ delete(oldPublicId)                 │
    │                │                  │─────────────────>│                  │
    │                │                  │                  │                  │
    │                │                  │ OK               │                  │
    │                │                  │<─────────────────│                  │
    │                │                  │                  │                  │
    │                │                  │ save(post)       │                  │
    │                │                  │─────────────────────────────────────>
    │                │                  │                  │                  │
    │                │ PostResponse     │                  │                  │
    │                │<─────────────────│                  │                  │
    │                │                  │                  │                  │
    │ 200 OK         │                  │                  │                  │
    │<───────────────│                  │                  │                  │
```
