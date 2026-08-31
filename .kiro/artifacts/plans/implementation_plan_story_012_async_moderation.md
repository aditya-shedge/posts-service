# Async Post Moderation using Kafka and OpenAI — Implementation Plan

## Overview

When a post is created, a moderation event is published to a Kafka topic. A Kafka consumer picks up the event, calls the OpenAI Moderation API to screen the post text, and updates the post status to `REJECTED` if harmful — or leaves it as `DRAFT` for human review if safe. API failures are retried with exponential backoff; exhausted retries default to approved.

## Architecture

```
POST /api/posts
    → PostService.createPost()
    → postRepository.save(post)
    → ModerationEventProducer.publish(postId, text)   [fire and forget]
    → return 201 immediately

Kafka topic: post-moderation
    → ModerationEventConsumer.consume(event)
    → OpenAIModerationService.moderate(text)
        → SAFE    : post stays DRAFT (human moderator reviews)
        → HARMFUL : post.status = REJECTED
        → FAILURE : retry with backoff → default DRAFT after max retries
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
- `src/main/java/com/example/posts_service/model/PostStatus.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`
- `src/test/java/com/example/posts_service/CreatePostIntegrationTest.java`

**What to do in this phase:**
- Verify that existing tests confirm post is created with `DRAFT` status
- Verify that the create post response is returned immediately (no blocking calls)
- Add characterization tests for any gaps before modifying createPost

**High-level characterization coverage:**
- Creating a post sets status to DRAFT
- Post creation returns 201 immediately without waiting for any downstream processing

---

### Phase 2: Docker Compose and Kafka Dependencies

**Files**:
- `docker-compose.yml`
- `build.gradle`
- `src/main/resources/application.properties`

**Test Files**: None

Set up Kafka locally and add Spring Kafka dependency.

**Key code changes:**

```yaml
# new file: docker-compose.yml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

```groovy
// build.gradle — new dependency
implementation 'org.springframework.kafka:spring-kafka'
testImplementation 'org.springframework.kafka:spring-kafka-test'
```

```properties
# application.properties — new Kafka config
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.group-id=posts-service
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.auto-offset-reset=earliest

app.kafka.topic.post-moderation=post-moderation
app.openai.api-key=${OPENAI_API_KEY}
app.openai.moderation-url=https://api.openai.com/v1/moderations
```

**Technical details:**
- Start Kafka with `docker-compose up -d` before running the app
- Spring Kafka auto-creates topics when `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`

---

### Phase 3: Moderation Event DTO and Kafka Producer

**Files**:
- `src/main/java/com/example/posts_service/dto/ModerationEvent.java`
- `src/main/java/com/example/posts_service/config/KafkaConfig.java`
- `src/main/java/com/example/posts_service/messaging/ModerationEventProducer.java`
- `src/main/java/com/example/posts_service/service/PostService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/messaging/ModerationEventProducerTest.java`
- `src/test/java/com/example/posts_service/service/PostServiceTest.java`

Create the Kafka producer and wire it into `createPost`.

**Key code changes:**

```java
// new file: ModerationEvent.java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ModerationEvent {
    private UUID postId;
    private String text;
    private LocalDateTime createdAt;
}
```

```java
// new file: KafkaConfig.java
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.post-moderation}")
    private String topicName;

    @Bean
    public NewTopic postModerationTopic() {
        return TopicBuilder.name(topicName).partitions(1).replicas(1).build();
    }
}
```

```java
// new file: ModerationEventProducer.java
@Component
public class ModerationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventProducer.class);

    private final KafkaTemplate<String, ModerationEvent> kafkaTemplate;

    @Value("${app.kafka.topic.post-moderation}")
    private String topicName;

    public ModerationEventProducer(KafkaTemplate<String, ModerationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UUID postId, String text) {
        ModerationEvent event = new ModerationEvent(postId, text, LocalDateTime.now());
        kafkaTemplate.send(topicName, postId.toString(), event);
        log.info("Published moderation event: postId={}", postId);
    }
}
```

```java
// existing code in PostService.createPost()
postRepository.save(post);

// new code — publish after save
moderationEventProducer.publish(post.getId(), post.getText());

return toResponse(post);
```

**Test scenarios:**
- Creating a post publishes a moderation event with the correct postId and text
- Moderation event is published after the post is saved, not before
- Kafka publish failure does not prevent post creation from succeeding (fire and forget)

**Technical details:**
- `KafkaTemplate.send()` is asynchronous by default — does not block the request
- Add `ModerationEventProducer` as a dependency to `PostService`
- Update `PostServiceTest` to mock `ModerationEventProducer`

---

### Phase 4: OpenAI Moderation Service

**Files**:
- `src/main/java/com/example/posts_service/dto/OpenAIModerationRequest.java`
- `src/main/java/com/example/posts_service/dto/OpenAIModerationResponse.java`
- `src/main/java/com/example/posts_service/service/OpenAIModerationService.java`

**Test Files**:
- `src/test/java/com/example/posts_service/service/OpenAIModerationServiceTest.java`

Create the service that calls the OpenAI Moderation API.

**Key code changes:**

```java
// new file: OpenAIModerationRequest.java
@Getter
@AllArgsConstructor
public class OpenAIModerationRequest {
    private String input;
}
```

```java
// new file: OpenAIModerationResponse.java
@Getter
@NoArgsConstructor
public class OpenAIModerationResponse {

    private List<Result> results;

    @Getter
    @NoArgsConstructor
    public static class Result {
        private boolean flagged;
        private Map<String, Boolean> categories;
    }

    public boolean isFlagged() {
        return results != null && !results.isEmpty() && results.get(0).isFlagged();
    }
}
```

```java
// new file: OpenAIModerationService.java
@Service
public class OpenAIModerationService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModerationService.class);

    private final RestClient restClient;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.moderation-url}")
    private String moderationUrl;

    public OpenAIModerationService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public boolean isFlagged(String text) {
        OpenAIModerationRequest request = new OpenAIModerationRequest(text);

        OpenAIModerationResponse response = restClient.post()
                .uri(moderationUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAIModerationResponse.class);

        boolean flagged = response != null && response.isFlagged();
        log.info("Moderation result: flagged={}", flagged);
        return flagged;
    }
}
```

**Test scenarios:**
- Returns `false` (not flagged) when OpenAI response has `flagged: false`
- Returns `true` (flagged) when OpenAI response has `flagged: true`
- Throws exception when API call fails (to be caught by retry logic in consumer)

**Technical details:**
- Use Spring's `RestClient` (available in Spring Boot 4.x) instead of `RestTemplate`
- Add `spring-boot-starter-web` is already included via `spring-boot-starter-webmvc`
- Mock the `RestClient` in unit tests — do not call the real API

---

### Phase 5: Kafka Consumer with Retry Logic

**Files**:
- `src/main/java/com/example/posts_service/messaging/ModerationEventConsumer.java`
- `src/main/java/com/example/posts_service/config/KafkaConfig.java`

**Test Files**:
- `src/test/java/com/example/posts_service/messaging/ModerationEventConsumerTest.java`

Create the Kafka consumer that calls OpenAI and updates post status.

**Key code changes:**

```java
// update: KafkaConfig.java — add retry configuration
@Bean
public DefaultErrorHandler errorHandler() {
    // Exponential backoff: 1min → 5min → 15min → give up (default to approved)
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(60_000L);   // 1 minute
    backOff.setMultiplier(5.0);            // 1min → 5min → 15min (approx)
    backOff.setMaxInterval(900_000L);      // cap at 15 minutes

    return new DefaultErrorHandler((record, exception) -> {
        // After all retries exhausted: log and leave post as DRAFT (default to approved)
        log.error("Moderation failed after all retries, defaulting to approved: {}", record.value());
    }, backOff);
}

@Bean
public ConcurrentKafkaListenerContainerFactory<String, ModerationEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, ModerationEvent> consumerFactory,
        DefaultErrorHandler errorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, ModerationEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
}
```

```java
// new file: ModerationEventConsumer.java
@Component
public class ModerationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventConsumer.class);

    private final PostRepository postRepository;
    private final OpenAIModerationService moderationService;

    public ModerationEventConsumer(PostRepository postRepository,
                                   OpenAIModerationService moderationService) {
        this.postRepository = postRepository;
        this.moderationService = moderationService;
    }

    @KafkaListener(topics = "${app.kafka.topic.post-moderation}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ModerationEvent event) {
        log.info("Received moderation event: postId={}", event.getPostId());

        boolean flagged = moderationService.isFlagged(event.getText());

        if (flagged) {
            postRepository.findById(event.getPostId()).ifPresent(post -> {
                post.setStatus(PostStatus.REJECTED);
                postRepository.save(post);
                log.info("Post rejected by AI moderation: postId={}", event.getPostId());
            });
        } else {
            log.info("Post approved by AI moderation, moving to human review: postId={}", event.getPostId());
        }
    }
}
```

**Test scenarios:**
- Safe content leaves post status as DRAFT
- Harmful content updates post status to REJECTED
- OpenAI API exception is propagated (triggers Kafka retry mechanism)
- Post not found in database is handled gracefully (logged, no error)

**Technical details:**
- Throwing an exception from `@KafkaListener` triggers the `DefaultErrorHandler` retry
- `ExponentialBackOffWithMaxRetries(3)` means 3 retries (4 total attempts)
- After retries exhausted, the recovery callback logs and returns — post stays DRAFT

---

### Phase 6: Database Schema for Moderation Audit

**Files**:
- `src/main/resources/db/migration/V10__add_moderation_columns.sql`
- `src/main/java/com/example/posts_service/model/Post.java`

**Test Files**: None (schema change)

Store the AI rejection reason on the post for audit purposes (AC3).

**Key code changes:**

```sql
-- V10__add_moderation_columns.sql
ALTER TABLE posts ADD COLUMN moderation_status VARCHAR(20);
ALTER TABLE posts ADD COLUMN moderation_reason TEXT;
ALTER TABLE posts ADD COLUMN moderated_at TIMESTAMP;
```

```java
// existing code in Post.java
// new code — add fields
@Column(name = "moderation_status", length = 20)
@Setter
private String moderationStatus;

@Column(name = "moderation_reason", columnDefinition = "TEXT")
@Setter
private String moderationReason;

@Column(name = "moderated_at")
@Setter
private LocalDateTime moderatedAt;
```

Update consumer to store reason:
```java
// existing code in ModerationEventConsumer.consume()
if (flagged) {
    postRepository.findById(event.getPostId()).ifPresent(post -> {
        post.setStatus(PostStatus.REJECTED);

// new code
        post.setModerationStatus("AI_REJECTED");
        post.setModerationReason("Content flagged by OpenAI Moderation API");
        post.setModeratedAt(LocalDateTime.now());
        postRepository.save(post);
    });
}
```

**Technical details:**
- Apply via psql: `psql -h localhost -U postgres -d postsdb -f V10__add_moderation_columns.sql`
- `moderationStatus` is a plain string (not enum) to keep flexibility for future values

---

### Phase 7: Integration Tests

**Files**: None (test files only)

**Test Files**:
- `src/test/java/com/example/posts_service/ModerationIntegrationTest.java`

End-to-end tests using embedded Kafka (`@EmbeddedKafka`).

**Test scenarios:**
- Creating a post publishes a message to the `post-moderation` topic
- Consumer receives message, calls moderation, and leaves DRAFT post as DRAFT when safe
- Consumer receives message, calls moderation, and updates post to REJECTED when flagged
- Post creation returns 201 immediately without waiting for consumer

**Technical details:**
- Use `@EmbeddedKafka` from `spring-kafka-test` to run Kafka in-process
- Mock `OpenAIModerationService` to control flagged/safe results
- Use `@DirtiesContext` if needed to reset Kafka state between tests
- Add `spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}` in test properties

---

## Technical Considerations

- **Dependencies**: `spring-kafka`, `spring-kafka-test`
- **Edge Cases**: Post deleted between event published and consumer processing, Kafka broker down at startup
- **Testing Strategy**: Unit tests for producer/consumer/OpenAI service, integration tests with embedded Kafka
- **Performance**: Producer is fire-and-forget (`kafkaTemplate.send()` non-blocking), consumer runs on separate thread pool
- **Security**: OpenAI API key stored in `.env`, never committed

## Testing Notes

- Write tests alongside production changes for each phase.
- Run unit tests individually per phase.
- Integration tests in Phase 7 require `spring-kafka-test` and `@EmbeddedKafka`.
- Do not call the real OpenAI API in tests — always mock `OpenAIModerationService`.

## Success Criteria

- [ ] `docker-compose up` starts Kafka and Zookeeper successfully
- [ ] Creating a post publishes a `ModerationEvent` to the `post-moderation` topic
- [ ] Consumer updates post status to `REJECTED` when OpenAI flags content
- [ ] Consumer leaves post as `DRAFT` when OpenAI approves content
- [ ] API failures are retried with exponential backoff (1min → 5min → 15min)
- [ ] Post defaults to `DRAFT` after retries exhausted (human moderator reviews)
- [ ] Rejection reason is stored on the post for audit
- [ ] Post creation response is not delayed by Kafka publishing
- [ ] All existing tests continue to pass
