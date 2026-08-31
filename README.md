# Posts Service

A Spring Boot REST API for a school posts management system. Teachers create posts, AI screens them for harmful content, and human moderators publish approved posts.

## Features

- **Post Management** — create, read, update, soft-delete posts
- **JWT Authentication** — stateless auth with role-based access control
- **Role-Based Authorization** — teachers manage their own posts, moderators review all
- **File Attachments** — upload images and documents to Cloudinary with background retry on failure
- **AI Content Moderation** — async moderation via Kafka and Hugging Face (hate speech detection)
- **Human Moderation Workflow** — moderators approve or reject AI-screened posts

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 4.1 |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Messaging | Apache Kafka |
| File Storage | Cloudinary |
| AI Moderation | Hugging Face Inference API |
| Build | Gradle |
| Testing | JUnit 5, Mockito, Testcontainers |

## Post Lifecycle

```
Teacher creates post (DRAFT)
        │
        ▼
Kafka publishes moderation event
        │
        ▼
Hugging Face screens content
        │
  ┌─────┴─────┐
  │           │
SAFE       HARMFUL
  │           │
DRAFT      REJECTED
(AI_APPROVED)
  │
  ▼
Moderator reviews
  │
  ├── approve ──► PUBLISHED
  └── reject  ──► REJECTED
```

## Getting Started

### Prerequisites

- Java 17
- PostgreSQL
- Docker (for Kafka)
- Cloudinary account (free tier)
- Hugging Face account (free tier)

### Setup

**1. Clone and configure environment**

```bash
git clone <repo-url>
cd posts-service
cp .env.example .env
```

Edit `.env` with your values:

```env
DB_URL=jdbc:postgresql://localhost:5432/postsdb
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

JWT_SECRET=your-256-bit-secret-key-minimum-32-characters!!

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

HUGGINGFACE_API_KEY=hf_your_token_here

KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

**2. Create the database**

```bash
createdb postsdb
```

**3. Start Kafka**

```bash
docker-compose up -d
```

**4. Run the application**

```bash
export $(cat .env | xargs) && ./gradlew bootRun
```

Flyway will run all migrations automatically on startup.

**5. Access Swagger UI**

```
http://localhost:8080/swagger-ui.html
```

## API Reference

### Authentication

```
POST /api/auth/login
```

```json
{
  "username": "teacher1",
  "password": "password123"
}
```

Returns a JWT token. Include it in all subsequent requests:
```
Authorization: Bearer <token>
```

### Posts

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/api/posts` | Teacher, Moderator | List posts (teachers see own, moderators see all DRAFT) |
| `GET` | `/api/posts/{id}` | Teacher, Moderator | Get a single post |
| `POST` | `/api/posts` | Teacher | Create a post (multipart/form-data) |
| `PUT` | `/api/posts/{id}` | Teacher | Update own DRAFT post |
| `DELETE` | `/api/posts/{id}` | Teacher | Soft-delete own DRAFT post |
| `POST` | `/api/posts/{id}/approve` | Moderator | Approve post → PUBLISHED |
| `POST` | `/api/posts/{id}/reject` | Moderator | Reject post → REJECTED |

### Create Post (multipart/form-data)

| Field | Type | Required |
|-------|------|----------|
| `text` | string | Yes |
| `remarks` | string | No |
| `attachment` | file | No (PDF, DOC, DOCX, JPG, PNG, GIF, max 5MB) |

### Post Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "text": "Class 5A field trip to Science Museum on Friday",
  "attachment": "https://res.cloudinary.com/...",
  "attachmentFilename": "permission-slip.pdf",
  "attachmentStatus": "UPLOADED",
  "remarks": "Please sign permission slips by Thursday",
  "status": "DRAFT",
  "moderationStatus": "AI_APPROVED",
  "moderationReason": null,
  "createdBy": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-08-18T10:30:00",
  "updatedAt": "2026-08-18T10:30:00"
}
```

## Seeded Test Users

| Username | Password | Roles |
|----------|----------|-------|
| `teacher1` | `password123` | TEACHER, MODERATOR |
| `teacher2` | `password123` | TEACHER |
| `moderator1` | `password123` | MODERATOR |

## Architecture

### File Attachment Upload

Files are uploaded to Cloudinary on post creation. If the upload fails, the post is saved with `attachmentStatus: PENDING` and retried in the background with exponential backoff (1min → 5min → 15min). After 3 failed retries the attachment is marked `FAILED`.

### AI Content Moderation

When a post is created, a `ModerationEvent` is published to the `post-moderation` Kafka topic. A consumer picks up the event and calls the Hugging Face `facebook/roberta-hate-speech-dynabench-r4-target` model. Posts with hate speech (`score > 0.5`) are automatically rejected. Safe posts remain as `DRAFT` and enter the human moderator queue.

## Running Tests

```bash
# All tests
./gradlew test

# Unit tests only
./gradlew test --tests "*.service.*" --tests "*.controller.*"

# Specific integration test
./gradlew test --tests "ModerationIntegrationTest"
```

Tests use Testcontainers for PostgreSQL and Kafka — no external services needed.

## Project Structure

```
src/
├── main/java/com/example/posts_service/
│   ├── config/          # Kafka, Cloudinary, RestClient config
│   ├── controller/      # REST controllers
│   ├── dto/             # Request/response DTOs
│   ├── exception/       # Custom exceptions and global handler
│   ├── messaging/       # Kafka producer and consumer
│   ├── model/           # JPA entities
│   ├── repository/      # Spring Data repositories
│   ├── scheduler/       # Attachment retry scheduler
│   ├── security/        # JWT filter, UserPrincipal
│   └── service/         # Business logic
└── main/resources/
    └── db/migration/    # Flyway SQL migrations (V1–V10)
```
