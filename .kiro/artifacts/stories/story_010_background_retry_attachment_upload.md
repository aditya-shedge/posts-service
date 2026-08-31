# User Story: Background Retry for Failed Attachment Uploads

## Business Context

When a teacher creates a post with an attachment, the Cloudinary upload may fail due to network issues, service unavailability, or rate limiting. Rather than failing the entire post creation (frustrating for the teacher), we create the post and retry the upload in the background.

This ensures teachers can continue working while the system handles transient failures automatically. If all retries fail, the attachment is marked as failed so the teacher knows to take action.

---

## Story

**As a** teacher,  
**I want** failed attachment uploads to be retried automatically in the background,  
**So that** my post is created immediately and I don't have to manually retry uploads due to temporary failures.

---

## Acceptance Criteria

### AC1: Failed uploads are queued for retry
**Given** a post was created with `attachmentStatus: PENDING`  
**When** the background job processor runs  
**Then** it picks up the pending attachment  
**And** attempts to upload the file to Cloudinary  

### AC2: Successful retry updates the post
**Given** a pending attachment is being retried  
**When** the Cloudinary upload succeeds  
**Then** the post's `attachment` field is updated with the Cloudinary URL  
**And** the `attachmentPublicId` is stored  
**And** the `attachmentStatus` is updated to `UPLOADED`  
**And** the temporary file is deleted  

### AC3: Retry with exponential backoff
**Given** a pending attachment upload fails  
**When** scheduling the next retry  
**Then** the retry delay increases exponentially (e.g., 1min, 5min, 15min)  
**And** a maximum of 3 retry attempts are made  

### AC4: Mark as failed after max retries
**Given** a pending attachment has failed 3 retry attempts  
**When** the final retry fails  
**Then** the `attachmentStatus` is updated to `FAILED`  
**And** the temporary file is deleted  
**And** the failure is logged for monitoring  

### AC5: Pending files are stored temporarily
**Given** a Cloudinary upload fails during post creation  
**When** the post is saved with `attachmentStatus: PENDING`  
**Then** the file is stored in temporary local storage  
**And** the file path is recorded for the retry job  

### AC6: Temporary storage is cleaned up
**Given** an attachment upload is complete (success or permanent failure)  
**When** the final status is set  
**Then** the temporary file is deleted from local storage  

---

## Out of Scope

- Distributed job processing (single instance is sufficient for MVP)
- Dead letter queue for manual intervention
- Teacher notifications when upload fails
- Admin dashboard for monitoring pending uploads

---

## Dependencies

- Story 009 (Upload Attachment When Creating Post) must be implemented
- Temporary file storage location must be configured
- Spring scheduling or a job framework must be available

---

## Assumptions

- Temporary files are stored on the application server's local filesystem
- The retry job runs on the same instance that created the post (no distributed storage needed for MVP)
- 3 retries with exponential backoff is sufficient for handling transient failures
- Permanent failures (e.g., invalid file) are detected on first attempt, not retried

---

## Technical Notes

### Database Changes
Add columns to `posts` table:
- `attachment_temp_path` — path to temporary file (nullable)
- `attachment_retry_count` — number of retry attempts (default 0)
- `attachment_next_retry_at` — timestamp for next retry attempt (nullable)

### Retry Schedule
| Attempt | Delay | Time from creation |
|---------|-------|-------------------|
| 1 | Immediate | 0 |
| 2 | 1 minute | 1 min |
| 3 | 5 minutes | 6 min |
| 4 | 15 minutes | 21 min |
| Final | Mark FAILED | — |

### Job Processing
Use Spring's `@Scheduled` annotation to poll for pending uploads:
```java
@Scheduled(fixedDelay = 30000) // every 30 seconds
public void processsPendingUploads() {
    List<Post> pending = postRepository.findPendingUploadsReadyForRetry(Instant.now());
    for (Post post : pending) {
        retryUpload(post);
    }
}
```

---

## Sequence Diagram

```
┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│ Scheduler  │     │  Service   │     │ Cloudinary │     │ PostgreSQL │
└─────┬──────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘
      │                  │                  │                  │
      │ @Scheduled       │                  │                  │
      │ (every 30s)      │                  │                  │
      │─────────────────>│                  │                  │
      │                  │                  │                  │
      │                  │ findPendingUploads()                │
      │                  │─────────────────────────────────────>
      │                  │                  │                  │
      │                  │ [posts]          │                  │
      │                  │<─────────────────────────────────────
      │                  │                  │                  │
      │                  │                  │                  │
      │          ┌───────┴───────┐         │                  │
      │          │ For each post │         │                  │
      │          └───────┬───────┘         │                  │
      │                  │                  │                  │
      │                  │ upload(tempFile) │                  │
      │                  │─────────────────>│                  │
      │                  │                  │                  │
      │                  │ {url, publicId}  │                  │
      │                  │<─────────────────│                  │
      │                  │                  │                  │
      │                  │ update(post)     │                  │
      │                  │ status=UPLOADED  │                  │
      │                  │─────────────────────────────────────>
      │                  │                  │                  │
      │                  │ delete tempFile  │                  │
      │                  │                  │                  │
      │                  │                  │                  │
```

### Failure Flow

```
┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│ Scheduler  │     │  Service   │     │ Cloudinary │     │ PostgreSQL │
└─────┬──────┘     └─────┬──────┘     └─────┬──────┘     └─────┬──────┘
      │                  │                  │                  │
      │                  │ upload(tempFile) │                  │
      │                  │─────────────────>│                  │
      │                  │                  │                  │
      │                  │ ERROR            │                  │
      │                  │<─────────────────│                  │
      │                  │                  │                  │
      │                  │ retryCount < 3?  │                  │
      │                  │                  │                  │
      │          ┌───────┴───────┐         │                  │
      │          │     YES       │         │                  │
      │          └───────┬───────┘         │                  │
      │                  │                  │                  │
      │                  │ update(post)     │                  │
      │                  │ retryCount++     │                  │
      │                  │ nextRetryAt=...  │                  │
      │                  │─────────────────────────────────────>
      │                  │                  │                  │
      │          ┌───────┴───────┐         │                  │
      │          │     NO        │         │                  │
      │          └───────┬───────┘         │                  │
      │                  │                  │                  │
      │                  │ update(post)     │                  │
      │                  │ status=FAILED    │                  │
      │                  │─────────────────────────────────────>
      │                  │                  │                  │
      │                  │ delete tempFile  │                  │
      │                  │                  │                  │
```
