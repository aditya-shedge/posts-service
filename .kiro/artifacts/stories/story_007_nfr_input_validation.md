# NFR Story: Input Validation

## Business Context

Data quality is essential for effective school communications. Invalid or malformed data can lead to confusion, broken links, or system errors. Proper input validation ensures that posts contain meaningful content, attachment URLs are properly formatted, and remarks stay within reasonable limits. Clear validation error messages help teachers correct their input without frustration.

---

## Story

**As a** system administrator,  
**I want** all API inputs to be validated against defined rules,  
**So that** the system maintains data integrity and provides helpful feedback when input is invalid.

---

## Acceptance Criteria

### AC1: Text field is required
**Given** a user submits a create or update post request  
**And** the text field is missing or blank (empty/whitespace only)  
**When** the server validates the request  
**Then** the request is rejected with a 400 Bad Request status  
**And** the error response clearly indicates that text is required  

### AC2: Attachment must be a valid URL when provided
**Given** a user submits a create or update post request  
**And** the attachment field contains a value that is not a valid URL format  
**When** the server validates the request  
**Then** the request is rejected with a 400 Bad Request status  
**And** the error response indicates that the attachment must be a valid URL  

### AC3: Empty attachment is allowed
**Given** a user submits a create or update post request  
**And** the attachment field is null or not provided  
**When** the server validates the request  
**Then** the validation passes for the attachment field  
**And** the post is created/updated with no attachment  

### AC4: Remarks must not exceed maximum length
**Given** a user submits a create or update post request  
**And** the remarks field contains more than 1000 characters  
**When** the server validates the request  
**Then** the request is rejected with a 400 Bad Request status  
**And** the error response indicates the remarks exceed the maximum length  

### AC5: Empty remarks is allowed
**Given** a user submits a create or update post request  
**And** the remarks field is null or not provided  
**When** the server validates the request  
**Then** the validation passes for the remarks field  
**And** the post is created/updated with no remarks  

### AC6: Multiple validation errors are reported together
**Given** a user submits a request with multiple invalid fields  
**When** the server validates the request  
**Then** all validation errors are reported in a single response  
**And** each error clearly identifies which field failed and why  

### AC7: Post ID must be valid UUID format
**Given** a user requests to view, update, or delete a post  
**And** the provided ID is not a valid UUID format  
**When** the server processes the request  
**Then** the request is rejected with a 400 Bad Request status  
**And** the error response indicates the ID format is invalid  

### AC8: Valid input passes validation
**Given** a user submits a request with valid data  
**And** text is non-blank  
**And** attachment (if provided) is a valid URL  
**And** remarks (if provided) is within the length limit  
**When** the server validates the request  
**Then** validation passes  
**And** the request proceeds to business logic processing  

---

## Out of Scope

- Content moderation or inappropriate language filtering
- Spam detection
- URL reachability verification (only format is validated)
- File type restrictions for attachment URLs
- Profanity filtering
- Character encoding validation beyond standard UTF-8
- Rate limiting (separate concern)

---

## Dependencies

- Spring Boot Validation starter must be included
- Bean Validation annotations (Jakarta Validation) must be configured
- Global exception handler must catch and format validation exceptions

---

## Assumptions

- Standard Bean Validation (JSR-380) annotations are used
- URL validation uses standard URL format rules (protocol://host/path)
- The 1000 character limit for remarks is sufficient for additional context
- Text field has no maximum length (stored as TEXT in database)
- Validation occurs before any business logic or database operations

---

## Validation Rules Summary

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| text | String | Yes | Not blank (cannot be empty or whitespace only) |
| attachment | String | No | Must be valid URL format if provided |
| remarks | String | No | Maximum 1000 characters if provided |
| id (path) | UUID | Yes | Must be valid UUID format |

---

## Error Response Format

**Single Validation Error (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "text: Text is required",
  "timestamp": "2026-08-18T10:00:00"
}
```

**Multiple Validation Errors (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "text: Text is required, attachment: Attachment must be a valid URL",
  "timestamp": "2026-08-18T10:00:00"
}
```

**Invalid UUID Format (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid post ID format",
  "timestamp": "2026-08-18T10:00:00"
}
```

---

## Valid URL Examples

The following are considered valid URL formats:
- `https://school-docs.example.com/document.pdf`
- `http://intranet.school.edu/resources/image.png`
- `https://drive.google.com/file/d/abc123/view`
- `ftp://files.school.edu/shared/worksheet.docx`

The following are invalid:
- `not-a-url`
- `www.example.com` (missing protocol)
- `://missing-host.com`
- `school docs/file.pdf` (contains spaces, no protocol)

---

## Mockups / Supporting Documents

### Validation Error Display (Frontend)

```
┌─────────────────────────────────────────────────────────────┐
│                     CREATE NEW POST                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Post Content *                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│  ⚠️ Text is required                                       │
│                                                             │
│  Attachment URL (optional)                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ not-a-valid-url                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│  ⚠️ Attachment must be a valid URL                         │
│                                                             │
│  Remarks (optional)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ [Very long text exceeding 1000 characters...]       │   │
│  └─────────────────────────────────────────────────────┘   │
│  ⚠️ Remarks must not exceed 1000 characters (1250/1000)    │
│                                                             │
│                              ┌──────────────┐              │
│                              │  Create Post │              │
│                              └──────────────┘              │
│                                 (disabled)                 │
└─────────────────────────────────────────────────────────────┘
```
