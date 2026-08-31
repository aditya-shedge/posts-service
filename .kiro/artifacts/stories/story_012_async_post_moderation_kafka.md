# User Story: Asynchronous Post Moderation using Kafka and AI

## Business Context

When a teacher creates a post, it must be reviewed before it is published. Currently, posts go straight to human moderators for review. This creates a backlog when many posts are submitted — moderators must review everything, including clearly inappropriate content.

By introducing an AI-powered first pass using the OpenAI Moderation API, flagged content is automatically rejected before it reaches the moderation queue. Only content that passes AI screening reaches human moderators. This reduces moderator workload and ensures harmful content is blocked immediately.

The moderation check runs asynchronously using Kafka, so post creation remains fast — the teacher receives an immediate response and moderation happens in the background.

---

## Story

**As a** school administrator,  
**I want** posts to be automatically screened for harmful content before reaching human moderators,  
**So that** moderators only review appropriate content and harmful posts are blocked immediately.

---

## Acceptance Criteria

### AC1: Post is published to Kafka after creation
**Given** a teacher successfully creates a post  
**When** the post is saved to the database  
**Then** a moderation event is published to the `post-moderation` Kafka topic  
**And** the event contains the post ID and text content  
**And** the post status remains `DRAFT`  

### AC2: AI-approved post moves to human moderation queue
**Given** a post event is consumed from the Kafka topic  
**When** the OpenAI Moderation API determines the content is safe  
**Then** the post status remains `DRAFT`  
**And** the post is available in the moderator's queue for human review  

### AC3: AI-rejected post is marked as rejected
**Given** a post event is consumed from the Kafka topic  
**When** the OpenAI Moderation API determines the content is harmful  
**Then** the post status is updated to `REJECTED`  
**And** the post is removed from the moderator's queue  
**And** the rejection reason is stored for audit purposes  

### AC4: Moderation API failure triggers retry
**Given** a post event is consumed from the Kafka topic  
**When** the OpenAI Moderation API call fails (network error, rate limit, timeout)  
**Then** the moderation is retried with exponential backoff  
**And** the post status remains `DRAFT` during retry attempts  

### AC5: Post defaults to approved after all retries are exhausted
**Given** a post event has failed moderation API retries  
**When** the maximum number of retries is exceeded  
**Then** the post status remains `DRAFT`  
**And** the post is available in the moderator's queue for human review  
**And** the failure is logged for monitoring  

### AC6: Post creation response is not delayed by moderation
**Given** a teacher submits a create post request  
**When** the post is saved to the database  
**Then** the teacher receives a `201 Created` response immediately  
**And** the response does not wait for moderation to complete  

---

## Out of Scope

- Real-time moderation status notifications to teachers
- Moderator UI showing AI rejection reason
- Configuring moderation sensitivity thresholds
- Moderating post attachments/images (text only)
- Manual re-submission of AI-rejected posts by teachers

---

## Dependencies

- Create Post API (Story 001) must be implemented
- Kafka must be running locally via Docker Compose
- OpenAI API key must be configured
- Human moderation workflow (approve/reject) must be in place

---

## Assumptions

- OpenAI Moderation API is free and does not require billing setup for basic usage
- Kafka runs locally via Docker Compose during development
- A single Kafka topic `post-moderation` is sufficient for this flow
- The post text is the only content sent for moderation (not attachments)
- AI moderation happens asynchronously — teachers are not notified of the result

---

## Technical Notes

### Post Status Flow
```
Teacher creates post
        ↓
   status = DRAFT
        ↓
   Published to Kafka
        ↓
   Consumer calls OpenAI
        ↓
  ┌─────────────────────┐
  │    OpenAI Result    │
  ├──────────┬──────────┤
  │  SAFE    │  HARMFUL │
  ├──────────┼──────────┤
  │  stays   │  status  │
  │  DRAFT   │  =       │
  │ (human   │ REJECTED │
  │  reviews)│          │
  └──────────┴──────────┘
```

### Kafka Event Schema
```json
{
  "postId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Class 5A field trip to Science Museum on Friday",
  "createdAt": "2026-08-26T10:30:00"
}
```

### Retry Schedule
| Attempt | Delay |
|---------|-------|
| 1 | Immediate |
| 2 | 1 minute |
| 3 | 5 minutes |
| 4 (final) | 15 minutes → default to approved |

### New Post Statuses
No new statuses are needed. Existing statuses cover the flow:
- `DRAFT` — created, awaiting moderation or human review
- `REJECTED` — rejected by AI moderation
- `PUBLISHED` — approved by human moderator
- `DELETED` — soft deleted by teacher

---

## Sequence Diagram

```
┌────────┐  ┌────────────┐  ┌────────────┐  ┌───────┐  ┌────────────┐  ┌──────────┐  ┌────────────┐
│Teacher │  │ Controller │  │PostService │  │ Kafka │  │ Consumer  │  │  OpenAI  │  │ PostgreSQL │
└───┬────┘  └─────┬──────┘  └─────┬──────┘  └───┬───┘  └─────┬──────┘  └────┬─────┘  └─────┬──────┘
    │              │               │              │             │              │              │
    │ POST /posts  │               │              │             │              │              │
    │─────────────>│               │              │             │              │              │
    │              │ createPost()  │              │             │              │              │
    │              │──────────────>│              │             │              │              │
    │              │               │ save(post)   │             │              │              │
    │              │               │─────────────────────────────────────────────────────────>
    │              │               │ publish(event)             │              │              │
    │              │               │─────────────>│             │              │              │
    │              │               │              │             │              │              │
    │              │ 201 Created   │              │             │              │              │
    │<─────────────│               │              │             │              │              │
    │              │               │              │ consume()   │              │              │
    │              │               │              │────────────>│              │              │
    │              │               │              │             │ moderate()   │              │
    │              │               │              │             │─────────────>│              │
    │              │               │              │             │ {safe/harm}  │              │
    │              │               │              │             │<─────────────│              │
    │              │               │              │             │ update(post) │              │
    │              │               │              │             │─────────────────────────────>
    │              │               │              │             │              │              │
```
