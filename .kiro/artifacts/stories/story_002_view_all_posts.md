# User Story: View All Posts

## Business Context

Teachers and parents need to stay informed about class-related updates, announcements, and shared resources. A centralized view of all posts allows users to browse through communications from various teachers, ensuring they don't miss important information about school activities, assignments, or events. Displaying posts in reverse chronological order ensures the most recent updates are seen first.

---

## Story

**As a** teacher or parent,  
**I want to** view all posts in the system,  
**So that** I can stay informed about class-related updates and announcements from all teachers.

---

## Acceptance Criteria

### AC1: View all posts ordered by most recent first
**Given** I am an authenticated user  
**And** there are multiple posts in the system  
**When** I request to view all posts  
**Then** I receive a list of all posts  
**And** the posts are ordered by creation date with the most recent first  

### AC2: View complete post details
**Given** I am an authenticated user  
**When** I view the list of posts  
**Then** each post displays the post ID, text content, attachment URL (if any), remarks (if any), creator ID, creation timestamp, and last updated timestamp  

### AC3: Empty list when no posts exist
**Given** I am an authenticated user  
**And** there are no posts in the system  
**When** I request to view all posts  
**Then** I receive an empty list  
**And** the response is successful (not an error)  

### AC4: View posts from all creators
**Given** I am an authenticated user  
**And** there are posts created by different teachers  
**When** I request to view all posts  
**Then** I can see posts from all teachers, not just my own  

---

## Out of Scope

- Pagination (all posts returned in single response)
- Filtering by creator, date range, or keywords
- Sorting options (always sorted by creation date descending)
- Search functionality
- Post previews or truncated content
- Unread/read status tracking

---

## Dependencies

- JWT Authentication must be implemented to verify user identity
- Posts must exist in the database (depends on Create Post functionality)
- Database query must support ordering by creation date

---

## Assumptions

- All authenticated users can view all posts (no role-based visibility restrictions)
- The number of posts is manageable without pagination for initial release
- Posts are returned with full content (no truncation)
- Soft-deleted posts (if any) are not included in the results

---

## API Specification

**Endpoint:** `GET /api/posts`

**Request Headers:**
```
Authorization: Bearer <jwt_token>
```

**Success Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "text": "Class 5A will have a field trip to the Science Museum on Friday.",
    "attachment": "https://school-docs.example.com/permission-slip-2026.pdf",
    "remarks": "Parents can contact me for any questions.",
    "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "createdAt": "2026-08-18T10:30:00",
    "updatedAt": "2026-08-18T10:30:00"
  },
  {
    "id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "text": "Reminder: Parent-teacher conference next Monday at 4 PM.",
    "attachment": null,
    "remarks": "Please confirm attendance by Friday.",
    "createdBy": "9c9e6679-7425-40de-944b-e07fc1f90ae8",
    "createdAt": "2026-08-17T14:00:00",
    "updatedAt": "2026-08-17T14:00:00"
  }
]
```

**Empty List Response (200 OK):**
```json
[]
```

---

## Mockups / Supporting Documents

```
┌─────────────────────────────────────────────────────────────┐
│                        ALL POSTS                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 📅 Aug 18, 2026 10:30 AM                            │   │
│  │                                                     │   │
│  │ Class 5A will have a field trip to the Science     │   │
│  │ Museum on Friday. Please ensure permission slips   │   │
│  │ are signed.                                        │   │
│  │                                                     │   │
│  │ 📎 permission-slip-2026.pdf                        │   │
│  │ 💬 Parents can contact me for any questions.       │   │
│  │                                                     │   │
│  │ Posted by: Teacher ID 7c9e6679...                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 📅 Aug 17, 2026 2:00 PM                             │   │
│  │                                                     │   │
│  │ Reminder: Parent-teacher conference next Monday    │   │
│  │ at 4 PM.                                           │   │
│  │                                                     │   │
│  │ 💬 Please confirm attendance by Friday.            │   │
│  │                                                     │   │
│  │ Posted by: Teacher ID 9c9e6679...                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
