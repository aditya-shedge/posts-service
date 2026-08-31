# Background Retry for Failed Attachment Uploads — Implementation Plan

## Overview

Refactor attachment storage into a dedicated `post_attachments` table (one-to-one with posts), then add a scheduled background job that retries failed Cloudinary uploads with exponential backoff.

## Architecture

```
post_attachments table (one-to-one with posts)
    ↑ written by PostService on create
    ↑ read/updated by AttachmentRetryService

@Scheduled (every 30s)
    → AttachmentRetryService.processPendingUploads()
    → AttachmentRepository.findPendingUploadsReadyForRetry(now)
    → For each attachment: CloudinaryService.upload(tempFile)
        → Success: set UPLOADED, delete temp file
        → Failure (retryCount < 3): increment retryCount, set nextRetryAt
        → Failure (retryCount >= 3): set FAILED, delete temp file
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/model/Post.java`
- `src/main/java/com/example/posts_service/repository/PostRepository.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/CreatePostAttachmentIntegrationTest.java`

**What to do in this phase:**
- Identify all tests that reference attachment fields on Post (attachment, attachmentStatus, attachmentFilename, etc.)
- Confirm they all pass before making any changes
- These tests will be updated in later phases as the model changes

**High-level characterization coverage:**
- Create post with attachment stores URL and status correctly
- Create post without attachment returns null attachment fields
- Cloudinary failure sets attachmentStatus to PENDING

---

### Phase 2: Database Schema Refactor

**Files**:
- `src/main/resources/db/migration/V9__create_post_attachments_table.sql`

**Test Files**: None (schema change verified by app startup and Flyway)

Create the `post_attachments` table and remove attachment columns from `posts`.

**Key code changes:**

```sql
-- V9__create_post_attachments_table.sql

CREATE TABLE post_attachments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL UNIQUE,
    url TEXT,
    public_id VARCHAR(255),
    filename VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    temp_path VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_post_attachments_post
        FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX idx_post_attachments_post_id ON post_attachments(post_id);
CREATE INDEX idx_post_attachments_status ON post_attachments(status);

-- Remove attachment columns from posts
ALTER TABLE posts DROP COLUMN IF EXISTS attachment;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_status;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_public_id;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_filename;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_temp_path;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_retry_count;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_next_retry_at;
```

**Technical details:**
- Apply manually via psql: `psql -h localhost -U postgres -d postsdb -f V9__create_post_attachments_table.sql`
- `post_id` is UNIQUE to enforce one-to-one relationship
- CASCADE delete ensures attachment is removed when post is deleted

---

### Phase 3: PostAttachment Model and Repository

**Files**:
- `src/main/java/com/example/posts_service/model/PostAttachment.java`
- `src/main/java/com/example/posts_service/repository/AttachmentRepository.java`
- `src/main/java/com/example/posts_service/model/Post.java`

**Test Files**:
- `src/test/java/com/example/posts_service/repository/AttachmentRepositoryTest.java`

Create the `PostAttachment` entity and remove attachment fields from `Post`.

**Key code changes:**

```java
// new file: PostAttachment.java
@Entity
@Table(name = "post_attachments")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostAttachment {

    @Id
    @Setter
    private UUID id;

    @Column(name = "post_id", nullable = false, unique = true)
    private UUID postId;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String url;

    @Column(name = "public_id")
    @Setter
    private String publicId;

    @Column(name = "filename")
    @Setter
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private AttachmentStatus status;

    @Column(name = "temp_path", length = 500)
    @Setter
    private String tempPath;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private int retryCount;

    @Column(name = "next_retry_at")
    @Setter
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onPersist() {
        createdAt = LocalDateTime.now();
    }
}
```

```java
// new file: AttachmentRepository.java
@Repository
public interface AttachmentRepository extends JpaRepository<PostAttachment, UUID> {

    Optional<PostAttachment> findByPostId(UUID postId);

    @Query("SELECT a FROM PostAttachment a WHERE a.status = 'PENDING' AND a.nextRetryAt <= :now")
    List<PostAttachment> findPendingUploadsReadyForRetry(@Param("now") LocalDateTime now);
}
```

```java
// existing code in Post.java — remove all attachment-related fields and setters
// The Post entity no longer contains any attachment fields
```

**Test scenarios:**
- Finding attachment by post ID returns the correct attachment
- Finding pending uploads ready for retry returns only past-due PENDING attachments
- Finding pending uploads does not return future-scheduled attachments

---

### Phase 4: Update PostService and PostResponse

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/dto/PostResponse.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/CreatePostAttachmentIntegrationTest.java`

Update PostService to save attachment to `post_attachments` table and update PostResponse to join from it.

**Key code changes:**

```java
// existing code in PostService
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;

// new code - add dependency
    private final AttachmentRepository attachmentRepository;

    public PostService(PostRepository postRepository,
                       CloudinaryService cloudinaryService,
                       FileValidationService fileValidationService,
                       AttachmentRepository attachmentRepository) {
        this.postRepository = postRepository;
        this.cloudinaryService = cloudinaryService;
        this.fileValidationService = fileValidationService;
        this.attachmentRepository = attachmentRepository;
    }

// new code - replace createPost
    public PostResponse createPost(String text, String remarks, MultipartFile attachment, UUID userId) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setText(text);
        post.setRemarks(remarks);
        post.setStatus(PostStatus.DRAFT);
        post.setCreatedBy(userId);
        postRepository.save(post);

        if (attachment != null && !attachment.isEmpty()) {
            fileValidationService.validate(attachment);
            PostAttachment postAttachment = new PostAttachment();
            postAttachment.setId(UUID.randomUUID());
            postAttachment.setPostId(post.getId()); // set via constructor
            postAttachment.setFilename(attachment.getOriginalFilename());

            try {
                CloudinaryUploadResult result = cloudinaryService.upload(attachment);
                postAttachment.setUrl(result.getUrl());
                postAttachment.setPublicId(result.getPublicId());
                postAttachment.setStatus(AttachmentStatus.UPLOADED);
            } catch (IOException e) {
                log.warn("Cloudinary upload failed, queuing for retry: postId={}", post.getId());
                postAttachment.setStatus(AttachmentStatus.PENDING);
                postAttachment.setRetryCount(0);
                postAttachment.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
                saveTempFile(postAttachment, attachment, post.getId());
            }

            attachmentRepository.save(postAttachment);
        }

        return toResponse(post, attachmentRepository.findByPostId(post.getId()).orElse(null));
    }

// new code - update toResponse to accept attachment
    private PostResponse toResponse(Post post, PostAttachment attachment) {
        return new PostResponse(
                post.getId(),
                post.getText(),
                attachment != null ? attachment.getUrl() : null,
                attachment != null ? attachment.getFilename() : null,
                attachment != null ? attachment.getStatus() : null,
                post.getRemarks(),
                post.getStatus(),
                post.getCreatedBy(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
```

**Test scenarios:**
- Create post with attachment saves PostAttachment with UPLOADED status
- Create post without attachment saves no PostAttachment, response has null attachment fields
- Cloudinary failure saves PostAttachment with PENDING status and temp path
- All other service methods (get, update, delete, approve, reject) still work

**Technical details:**
- All existing service methods that call `toResponse(post)` need to be updated to `toResponse(post, attachmentRepository.findByPostId(post.getId()).orElse(null))`
- This adds one extra query per response — acceptable for MVP

---

### Phase 5: AttachmentRetryService

**Files**:
- `src/main/java/com/example/posts_service/service/AttachmentRetryService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/AttachmentRetryServiceTest.java`

Core retry logic — polls for pending attachments and retries uploads.

**Key code changes:**

```java
// new file: AttachmentRetryService.java
@Service
public class AttachmentRetryService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRetryService.class);
    private static final int MAX_RETRY_COUNT = 3;
    private static final int[] RETRY_DELAYS_MINUTES = {1, 5, 15};

    private final AttachmentRepository attachmentRepository;
    private final CloudinaryService cloudinaryService;

    public AttachmentRetryService(AttachmentRepository attachmentRepository,
                                  CloudinaryService cloudinaryService) {
        this.attachmentRepository = attachmentRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public void processPendingUploads() {
        List<PostAttachment> pending = attachmentRepository.findPendingUploadsReadyForRetry(LocalDateTime.now());
        log.info("Processing {} pending attachment uploads", pending.size());
        pending.forEach(this::retryUpload);
    }

    void retryUpload(PostAttachment attachment) {
        String tempPath = attachment.getTempPath();

        if (tempPath == null || !Files.exists(Paths.get(tempPath))) {
            log.warn("Temp file missing for attachment: id={}", attachment.getId());
            markAsFailed(attachment);
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(tempPath));
            String contentType = Files.probeContentType(Paths.get(tempPath));
            MockMultipartFile file = new MockMultipartFile(
                    "attachment", attachment.getFilename(), contentType, fileBytes);

            CloudinaryUploadResult result = cloudinaryService.upload(file);
            attachment.setUrl(result.getUrl());
            attachment.setPublicId(result.getPublicId());
            attachment.setStatus(AttachmentStatus.UPLOADED);
            attachment.setTempPath(null);
            attachment.setNextRetryAt(null);
            attachmentRepository.save(attachment);
            deleteTempFile(tempPath);

            log.info("Retry succeeded: attachmentId={}", attachment.getId());

        } catch (IOException e) {
            int retryCount = attachment.getRetryCount();
            if (retryCount >= MAX_RETRY_COUNT - 1) {
                markAsFailed(attachment);
                deleteTempFile(tempPath);
            } else {
                attachment.setRetryCount(retryCount + 1);
                attachment.setNextRetryAt(LocalDateTime.now().plusMinutes(RETRY_DELAYS_MINUTES[retryCount]));
                attachmentRepository.save(attachment);
                log.warn("Retry failed, will retry later: attachmentId={}, attempt={}", attachment.getId(), retryCount + 1);
            }
        }
    }

    private void markAsFailed(PostAttachment attachment) {
        attachment.setStatus(AttachmentStatus.FAILED);
        attachment.setTempPath(null);
        attachment.setNextRetryAt(null);
        attachmentRepository.save(attachment);
        log.error("Attachment permanently failed: attachmentId={}, postId={}", attachment.getId(), attachment.getPostId());
    }

    private void deleteTempFile(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("Failed to delete temp file: path={}", path);
        }
    }
}
```

**Test scenarios:**
- Successful retry sets status to UPLOADED with URL and public ID
- Successful retry deletes the temp file
- Failed retry below max retries increments retryCount and sets next nextRetryAt with correct backoff
- Failed retry at max retries sets status to FAILED and deletes temp file
- Missing temp file marks attachment as FAILED without calling Cloudinary
- Retry delays follow schedule: attempt 0→1min, attempt 1→5min, attempt 2→15min

---

### Phase 6: Scheduler and Integration Tests

**Files**:
- `src/main/java/com/example/posts_service/PostsServiceApplication.java`
- `src/main/java/com/example/posts_service/scheduler/AttachmentRetryScheduler.java`
- `src/main/resources/application.properties`

**Test Files**:
- `src/test/java/com/example/posts_service/scheduler/AttachmentRetrySchedulerTest.java`
- `src/test/java/com/example/posts_service/AttachmentRetryIntegrationTest.java`

Wire scheduler and add integration tests.

**Key code changes:**

```java
// existing code
@SpringBootApplication
public class PostsServiceApplication { ... }

// new code
@SpringBootApplication
@EnableScheduling
public class PostsServiceApplication { ... }
```

```java
// new file: AttachmentRetryScheduler.java
@Component
public class AttachmentRetryScheduler {

    private final AttachmentRetryService attachmentRetryService;

    public AttachmentRetryScheduler(AttachmentRetryService attachmentRetryService) {
        this.attachmentRetryService = attachmentRetryService;
    }

    @Scheduled(fixedDelayString = "${app.attachment.retry-interval-ms:30000}")
    public void processPendingUploads() {
        attachmentRetryService.processPendingUploads();
    }
}
```

```properties
# application.properties
app.attachment.retry-interval-ms=30000
```

**Scheduler test scenarios:**
- Scheduler delegates to `AttachmentRetryService.processPendingUploads()`

**Integration test scenarios:**
- Pending attachment with past nextRetryAt is picked up and updated to UPLOADED on success
- Pending attachment with future nextRetryAt is not picked up
- Pending attachment reaching max retries is marked FAILED
- Temp file is deleted after successful upload
- Temp file is deleted after permanent failure

---

## Technical Considerations

- **Dependencies**: No new dependencies — `@Scheduled` is in `spring-boot-starter`
- **Edge Cases**: Missing temp file, null retryCount, attachment deleted between query and retry
- **Testing Strategy**: Unit tests for retry logic, integration tests for DB flow with mocked Cloudinary
- **Performance**: Extra attachment lookup per `toResponse()` call is acceptable for MVP — can be optimized with JOIN later
- **Security**: Temp files deleted after use, no sensitive data persists

## Testing Notes

- Write tests alongside production changes for each phase.
- Run tests for each phase before moving to the next.
- Mock `CloudinaryService` in all tests.

## Success Criteria

- [ ] Attachment data is stored in `post_attachments` table, not in `posts`
- [ ] `PostResponse` still returns flat attachment fields
- [ ] Posts with PENDING attachment are retried every 30 seconds
- [ ] Successful retries update attachment to UPLOADED with Cloudinary URL
- [ ] After 3 failed retries attachment is marked FAILED
- [ ] Temp files are cleaned up after success or permanent failure
- [ ] All existing tests pass
