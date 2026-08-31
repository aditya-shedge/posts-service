# Upload Attachment When Creating Post — Implementation Plan

## Overview

Enhance the Create Post API to support file uploads. Files are uploaded to Cloudinary and the resulting URL is stored with the post. If the upload fails, the post is created with `attachmentStatus: PENDING` for background retry (Story 010).

## Architecture

```
POST /api/posts (multipart/form-data)
    → PostController (receives MultipartFile)
    → PostService (validates file, calls CloudinaryService)
    → CloudinaryService (uploads to Cloudinary)
    → PostRepository (saves post with attachment URL)
```

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**: 
- `src/main/java/com/example/posts_service/controller/PostController.java`
- `src/main/java/com/example/posts_service/service/PostService.java`
- `src/main/java/com/example/posts_service/dto/CreatePostRequest.java`

**Test Files**:
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

**What to do in this phase:**
- Identify the specific behaviours in createPost that will change (currently accepts JSON with URL string)
- Verify existing tests cover the current createPost behaviour
- Add characterization tests if any gaps exist in coverage for the create post flow

**High-level characterization coverage:**
- Creating a post with text only succeeds and returns the post
- Creating a post with text and attachment URL succeeds
- Creating a post with blank text fails validation

---

### Phase 2: Database Schema Changes

**Files**: 
- `src/main/resources/db/migration/V8__add_attachment_columns.sql`
- `src/main/java/com/example/posts_service/model/Post.java`
- `src/main/java/com/example/posts_service/model/AttachmentStatus.java`

**Test Files**: None (schema changes verified by app startup)

Add new columns to support attachment metadata and retry mechanism.

**Key code changes:**

```sql
-- V8__add_attachment_columns.sql
ALTER TABLE posts ADD COLUMN attachment_status VARCHAR(20);
ALTER TABLE posts ADD COLUMN attachment_public_id VARCHAR(255);
ALTER TABLE posts ADD COLUMN attachment_filename VARCHAR(255);
ALTER TABLE posts ADD COLUMN attachment_temp_path VARCHAR(500);
ALTER TABLE posts ADD COLUMN attachment_retry_count INTEGER DEFAULT 0;
ALTER TABLE posts ADD COLUMN attachment_next_retry_at TIMESTAMP;
```

```java
// new file: AttachmentStatus.java
public enum AttachmentStatus {
    PENDING,
    UPLOADED,
    FAILED
}
```

```java
// existing code in Post.java
@Entity
@Table(name = "posts")
public class Post {
    // ... existing fields ...

// new code
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_status", length = 20)
    @Setter
    private AttachmentStatus attachmentStatus;

    @Column(name = "attachment_public_id")
    @Setter
    private String attachmentPublicId;

    @Column(name = "attachment_filename")
    @Setter
    private String attachmentFilename;

    @Column(name = "attachment_temp_path", length = 500)
    @Setter
    private String attachmentTempPath;

    @Column(name = "attachment_retry_count")
    @Setter
    private Integer attachmentRetryCount;

    @Column(name = "attachment_next_retry_at")
    @Setter
    private LocalDateTime attachmentNextRetryAt;
}
```

**Technical details:**
- Run migration via psql: `psql -h localhost -U postgres -d postsdb -f src/main/resources/db/migration/V8__add_attachment_columns.sql`
- Update Post constructor to include new fields (set to null by default)

---

### Phase 3: Cloudinary Configuration and Service

**Files**:
- `build.gradle`
- `src/main/resources/application.properties`
- `src/main/java/com/example/posts_service/config/CloudinaryConfig.java`
- `src/main/java/com/example/posts_service/service/CloudinaryService.java`
- `src/main/java/com/example/posts_service/dto/CloudinaryUploadResult.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/CloudinaryServiceTest.java`

Add Cloudinary SDK and create a service to handle uploads.

**Key code changes:**

```groovy
// build.gradle - add dependency
implementation 'com.cloudinary:cloudinary-http44:1.36.0'
implementation 'me.paulschwarz:spring-dotenv:4.0.0'
```

```properties
# application.properties
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}

spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB
```

```java
// new file: CloudinaryConfig.java
@Configuration
public class CloudinaryConfig {
    
    @Value("${cloudinary.cloud-name}")
    private String cloudName;
    
    @Value("${cloudinary.api-key}")
    private String apiKey;
    
    @Value("${cloudinary.api-secret}")
    private String apiSecret;
    
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret
        ));
    }
}
```

```java
// new file: CloudinaryUploadResult.java
@Getter
@AllArgsConstructor
public class CloudinaryUploadResult {
    private String url;
    private String publicId;
}
```

```java
// new file: CloudinaryService.java
@Service
public class CloudinaryService {
    
    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    
    private final Cloudinary cloudinary;
    
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }
    
    public CloudinaryUploadResult upload(MultipartFile file) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "posts",
            "resource_type", "auto"
        );
        
        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
        
        String url = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");
        
        log.info("Uploaded file to Cloudinary: publicId={}", publicId);
        
        return new CloudinaryUploadResult(url, publicId);
    }
    
    public void delete(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        log.info("Deleted file from Cloudinary: publicId={}", publicId);
    }
}
```

**Test scenarios:**
- Upload succeeds and returns URL and public ID
- Upload throws IOException on Cloudinary failure

**Technical details:**
- `resource_type: auto` allows Cloudinary to detect file type (image vs raw for PDFs)
- Use `secure_url` for HTTPS URLs

---

### Phase 4: File Validation

**Files**:
- `src/main/java/com/example/posts_service/service/FileValidationService.java`
- `src/main/java/com/example/posts_service/exception/InvalidFileException.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/FileValidationServiceTest.java`

Create validation service for file type and size.

**Key code changes:**

```java
// new file: InvalidFileException.java
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
```

```java
// new file: FileValidationService.java
@Service
public class FileValidationService {
    
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png", 
        "image/gif",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    
    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File size exceeds maximum allowed size of 5 MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileException(
                "File type not allowed. Allowed types: PDF, DOC, DOCX, JPG, PNG, GIF"
            );
        }
    }
}
```

```java
// existing code in GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // ... existing handlers ...

// new code
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileException(InvalidFileException ex) {
        ErrorResponse error = new ErrorResponse(400, "Bad Request", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ErrorResponse error = new ErrorResponse(400, "Bad Request", 
            "File size exceeds maximum allowed size of 5 MB");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

**Test scenarios:**
- Valid JPEG file passes validation
- Valid PDF file passes validation
- File exceeding 5 MB throws InvalidFileException
- Invalid file type (e.g., .exe) throws InvalidFileException
- Empty file throws InvalidFileException

---

### Phase 5: Update DTOs and Response

**Files**:
- `src/main/java/com/example/posts_service/dto/PostResponse.java`

**Test Files**: None (covered by integration tests)

Add new fields to PostResponse for attachment metadata.

**Key code changes:**

```java
// existing code
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private UUID id;
    private String text;
    private String attachment;
    private String remarks;
    private PostStatus status;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

// new code - add fields
    private String attachmentFilename;
    private AttachmentStatus attachmentStatus;
}
```

**Technical details:**
- Update the `toResponse()` method in PostService to include new fields
- AttachmentStatus will be null for posts without attachments

---

### Phase 6: Update Controller for Multipart

**Files**:
- `src/main/java/com/example/posts_service/controller/PostController.java`

**Test Files**:
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

Change createPost endpoint to accept multipart/form-data instead of JSON.

**Key code changes:**

```java
// existing code
@RestController
@RequestMapping("/api/posts")
public class PostController {
    
    private final PostService postService;
    
    // ... other methods ...

// new code - replace existing createPost
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("text") @NotBlank(message = "Text is required") String text,
            @RequestParam(value = "remarks", required = false) 
                @Size(max = 1000, message = "Remarks must not exceed 1000 characters") String remarks,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.createPost(text, remarks, attachment, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

**Test scenarios:**
- Create post with text only returns 201
- Create post with text and valid attachment returns 201 with attachment URL
- Create post with invalid file type returns 400
- Create post with file exceeding 5 MB returns 400
- Request without authentication returns 401

---

### Phase 7: Update PostService with Upload Logic

**Files**:
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

Modify createPost to handle file uploads and Cloudinary integration.

**Key code changes:**

```java
// existing code
@Service
public class PostService {
    
    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    
    private final PostRepository postRepository;

// new code - add dependencies
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;
    
    @Value("${app.attachment.temp-dir:${java.io.tmpdir}/posts-attachments}")
    private String tempDir;
    
    public PostService(PostRepository postRepository, 
                       CloudinaryService cloudinaryService,
                       FileValidationService fileValidationService) {
        this.postRepository = postRepository;
        this.cloudinaryService = cloudinaryService;
        this.fileValidationService = fileValidationService;
    }

// new code - replace existing createPost
    public PostResponse createPost(String text, String remarks, MultipartFile attachment, UUID userId) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setText(text);
        post.setRemarks(remarks);
        post.setStatus(PostStatus.DRAFT);
        post.setCreatedBy(userId);
        
        if (attachment != null && !attachment.isEmpty()) {
            fileValidationService.validate(attachment);
            post.setAttachmentFilename(attachment.getOriginalFilename());
            
            try {
                CloudinaryUploadResult result = cloudinaryService.upload(attachment);
                post.setAttachment(result.getUrl());
                post.setAttachmentPublicId(result.getPublicId());
                post.setAttachmentStatus(AttachmentStatus.UPLOADED);
                log.info("Attachment uploaded successfully: postId={}, publicId={}", 
                    post.getId(), result.getPublicId());
            } catch (IOException e) {
                log.warn("Cloudinary upload failed, queuing for retry: postId={}, error={}", 
                    post.getId(), e.getMessage());
                post.setAttachmentStatus(AttachmentStatus.PENDING);
                post.setAttachmentRetryCount(0);
                post.setAttachmentNextRetryAt(LocalDateTime.now().plusMinutes(1));
                saveAttachmentToTemp(post, attachment);
            }
        }
        
        Post saved = postRepository.save(post);
        log.info("Created post: id={}, createdBy={}, attachmentStatus={}", 
            saved.getId(), userId, saved.getAttachmentStatus());
        
        return toResponse(saved);
    }
    
    private void saveAttachmentToTemp(Post post, MultipartFile file) {
        try {
            Path tempDirPath = Paths.get(tempDir);
            Files.createDirectories(tempDirPath);
            
            String filename = post.getId() + "_" + file.getOriginalFilename();
            Path tempFile = tempDirPath.resolve(filename);
            file.transferTo(tempFile);
            
            post.setAttachmentTempPath(tempFile.toString());
            log.info("Saved attachment to temp: path={}", tempFile);
        } catch (IOException e) {
            log.error("Failed to save attachment to temp: postId={}, error={}", 
                post.getId(), e.getMessage());
            post.setAttachmentStatus(AttachmentStatus.FAILED);
        }
    }
    
// existing code - update toResponse
    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getText(),
                post.getAttachment(),
                post.getRemarks(),
                post.getStatus(),
                post.getCreatedBy(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getAttachmentFilename(),
                post.getAttachmentStatus()
        );
    }
}
```

**Test scenarios:**
- Create post without attachment succeeds, attachmentStatus is null
- Create post with attachment uploads to Cloudinary, attachmentStatus is UPLOADED
- Create post when Cloudinary fails sets attachmentStatus to PENDING
- Original filename is preserved in attachmentFilename
- File validation is called before upload attempt

---

### Phase 8: Integration Tests

**Files**: None (test files only)

**Test Files**:
- `src/test/java/com/example/posts_service/CreatePostAttachmentIntegrationTest.java`

Create integration tests for the complete upload flow.

**Test scenarios:**
- Create post with valid image attachment returns 201 with attachment URL
- Create post with valid PDF attachment returns 201 with attachment URL
- Create post without attachment returns 201 with null attachment fields
- Create post with invalid file type returns 400
- Create post with oversized file returns 400
- Attachment filename is preserved in response

**Technical details:**
- Use MockMultipartFile for file uploads in tests
- Mock CloudinaryService in integration tests to avoid actual API calls
- Test both success and failure paths

---

## Technical Considerations

- **Dependencies**: Cloudinary SDK (`cloudinary-http44:1.36.0`), spring-dotenv for .env loading
- **Edge Cases**: Empty files, null content types, network failures during upload
- **Testing Strategy**: Unit tests for services, integration tests with mocked Cloudinary
- **Performance**: File uploads are synchronous; large files may cause request timeout (mitigated by 5MB limit)
- **Security**: File type validation prevents malicious uploads; Cloudinary handles file storage security

## Testing Notes

- Write tests alongside the production changes for each phase.
- Run tests for each phase before moving to the next.
- Mock CloudinaryService in tests to avoid hitting real API and incurring costs.

## Success Criteria

- [ ] Posts can be created with file attachments (PDF, DOC, DOCX, JPG, PNG, GIF)
- [ ] File size validation rejects files larger than 5 MB
- [ ] File type validation rejects unsupported formats
- [ ] Successful uploads store Cloudinary URL and public ID
- [ ] Failed uploads set status to PENDING for background retry
- [ ] Original filename is preserved and returned in response
- [ ] All existing tests continue to pass
- [ ] New tests cover upload success, failure, and validation scenarios
