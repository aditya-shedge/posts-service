# User Story: Upload Attachment When Creating a Post

## Business Context

Teachers often need to share documents and images alongside their posts — permission slips, class photos, assignment sheets, etc. Currently, teachers must host files elsewhere and paste URLs manually. This creates friction and relies on external hosting that may not be permanent.

By integrating file upload directly into the Create Post flow, teachers can attach files seamlessly. Files are uploaded to Cloudinary (a reliable third-party storage service) and the resulting URL is stored with the post.

---

## Story

**As a** teacher,  
**I want to** upload an image or document when creating a post,  
**So that** I can share files directly without needing to host them elsewhere.

---

## Acceptance Criteria

### AC1: Successfully create a post with an attachment
**Given** I am an authenticated teacher  
**When** I submit a create post request with text content and an attached file (image or document)  
**Then** the file is uploaded to Cloudinary  
**And** the post is created with the Cloudinary URL stored as the attachment  
**And** the `attachmentStatus` is set to `UPLOADED`  
**And** I receive a response with the created post details including the attachment URL  

### AC2: Create a post without an attachment
**Given** I am an authenticated teacher  
**When** I submit a create post request with only text content (no file attached)  
**Then** the post is created successfully  
**And** the attachment field is null  
**And** the `attachmentStatus` is null  

### AC3: Allowed file types are validated
**Given** I am an authenticated teacher  
**When** I attempt to upload a file that is not PDF, DOC, DOCX, JPG, PNG, or GIF  
**Then** the request is rejected with a 400 Bad Request  
**And** the error message indicates the file type is not allowed  

### AC4: File size limit is enforced
**Given** I am an authenticated teacher  
**When** I attempt to upload a file larger than 5 MB  
**Then** the request is rejected with a 400 Bad Request  
**And** the error message indicates the file exceeds the maximum size  

### AC5: Cloudinary upload failure triggers background retry
**Given** I am an authenticated teacher  
**And** I submit a create post request with an attached file  
**When** the Cloudinary upload fails (network error, service unavailable, etc.)  
**Then** the post is created successfully  
**And** the `attachmentStatus` is set to `PENDING`  
**And** the file is queued for background retry  
**And** I receive a response indicating the post was created but attachment upload is in progress  

### AC6: Original filename is preserved
**Given** I am an authenticated teacher  
**When** I upload an attachment  
**Then** the original filename is stored with the post for display purposes  

---

## Out of Scope

- Multiple attachments per post
- Attachment preview/thumbnail generation
- Virus scanning of uploaded files
- Attachment compression or optimization
- Direct download from our API (users access Cloudinary URL directly)

---

## Dependencies

- Cloudinary account with API credentials configured
- Create Post API (Story 001) must be implemented
- Background job processing infrastructure for retry mechanism (Story 010)

---

## Assumptions

- Cloudinary free tier provides sufficient storage and bandwidth for initial usage
- File uploads are sent as multipart/form-data requests
- The Cloudinary public ID will be stored to enable future deletion
- Teachers trust Cloudinary as a third-party storage provider

---

## Technical Notes

### Database Changes
Add columns to `posts` table:
- `attachment_status` — enum: `PENDING`, `UPLOADED`, `FAILED` (nullable)
- `attachment_public_id` — Cloudinary public ID for deletion (nullable)
- `attachment_filename` — original filename (nullable)

### API Changes
The Create Post endpoint changes from `application/json` to `multipart/form-data`:
- `text` — required, string
- `remarks` — optional, string  
- `attachment` — optional, file

---

## API Specification

**Endpoint:** `POST /api/posts`

**Request Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data
```

**Request Body (multipart/form-data):**
```
text: "Class 5A field trip to Science Museum on Friday"
remarks: "Please sign permission slips by Thursday"
attachment: [file binary - permission-slip.pdf]
```

**Success Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Class 5A field trip to Science Museum on Friday",
  "attachment": "https://res.cloudinary.com/school/image/upload/v1234/posts/abc123.pdf",
  "attachmentFilename": "permission-slip.pdf",
  "attachmentStatus": "UPLOADED",
  "remarks": "Please sign permission slips by Thursday",
  "status": "DRAFT",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-21T10:30:00",
  "updatedAt": "2026-08-21T10:30:00"
}
```

**Success Response with Pending Attachment (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Class 5A field trip to Science Museum on Friday",
  "attachment": null,
  "attachmentFilename": "permission-slip.pdf",
  "attachmentStatus": "PENDING",
  "remarks": "Please sign permission slips by Thursday",
  "status": "DRAFT",
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-21T10:30:00",
  "updatedAt": "2026-08-21T10:30:00"
}
```

**Error Response - Invalid File Type (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF",
  "timestamp": "2026-08-21T10:30:00"
}
```

**Error Response - File Too Large (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "File size exceeds maximum allowed size of 5 MB",
  "timestamp": "2026-08-21T10:30:00"
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
│  │ Class 5A field trip to Science Museum on Friday     │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Attachment (optional)                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  📎 permission-slip.pdf (2.3 MB)          [Remove]  │   │
│  └─────────────────────────────────────────────────────┘   │
│  Allowed: PDF, DOC, DOCX, JPG, PNG, GIF (max 5 MB)         │
│                                                             │
│  Remarks (optional)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Please sign permission slips by Thursday            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│                              ┌──────────────┐              │
│                              │  Create Post │              │
│                              └──────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

### Sequence Diagram

```
┌────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│Teacher │     │ Controller │     │  Service   │     │ Cloudinary │     │ PostgreSQL │
└───┬────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘
    │                │                  │                  │                  │
    │ POST /api/posts│                  │                  │                  │
    │ (multipart)    │                  │                  │                  │
    │───────────────>│                  │                  │                  │
    │                │                  │                  │                  │
    │                │ createPost()     │                  │                  │
    │                │─────────────────>│                  │                  │
    │                │                  │                  │                  │
    │                │                  │ upload(file)     │                  │
    │                │                  │─────────────────>│                  │
    │                │                  │                  │                  │
    │                │                  │ {url, publicId}  │                  │
    │                │                  │<─────────────────│                  │
    │                │                  │                  │                  │
    │                │                  │ save(post)       │                  │
    │                │                  │─────────────────────────────────────>
    │                │                  │                  │                  │
    │                │                  │ post             │                  │
    │                │                  │<─────────────────────────────────────
    │                │                  │                  │                  │
    │                │ PostResponse     │                  │                  │
    │                │<─────────────────│                  │                  │
    │                │                  │                  │                  │
    │ 201 Created    │                  │                  │                  │
    │<───────────────│                  │                  │                  │
    │                │                  │                  │                  │
```
