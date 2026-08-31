package com.example.posts_service;

import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.service.CloudinaryService;
import com.example.posts_service.service.HuggingFaceModerationService;
import com.example.posts_service.util.TestJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {com.example.posts_service.PostsServiceApplication.class, com.example.posts_service.config.TestKafkaConfig.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
class ModerationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private HuggingFaceModerationService openAIModerationService;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        postRepository.deleteAll();
    }

    @Test
    void createPostReturns201ImmediatelyWithDraftStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        when(openAIModerationService.isFlagged(anyString())).thenReturn(false);

        mockMvc.perform(multipart("/api/posts")
                        .param("text", "Field trip announcement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void safePostRemainsAsDraftAfterConsumerProcesses() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        when(openAIModerationService.isFlagged(anyString())).thenReturn(false);

        String responseBody = mockMvc.perform(multipart("/api/posts")
                        .param("text", "Class trip to the science museum")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String postId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Post post = postRepository.findById(UUID.fromString(postId)).orElseThrow();
            assertEquals(PostStatus.DRAFT, post.getStatus());
            assertEquals("AI_APPROVED", post.getModerationStatus());
        });
    }

    @Test
    void flaggedPostIsRejectedAfterConsumerProcesses() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        when(openAIModerationService.isFlagged(anyString())).thenReturn(true);

        String responseBody = mockMvc.perform(multipart("/api/posts")
                        .param("text", "Harmful content here")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String postId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Post post = postRepository.findById(UUID.fromString(postId)).orElseThrow();
            assertEquals(PostStatus.REJECTED, post.getStatus());
            assertEquals("AI_REJECTED", post.getModerationStatus());
            assertNotNull(post.getModerationReason());
            assertNotNull(post.getModeratedAt());
        });
    }

    @Test
    void postExistsInDatabaseImmediatelyAfterCreation() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtUtil.generateToken(userId, "teacher");

        when(openAIModerationService.isFlagged(anyString())).thenReturn(false);

        String responseBody = mockMvc.perform(multipart("/api/posts")
                        .param("text", "Announcement text")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String postId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

        assertNotNull(postRepository.findById(UUID.fromString(postId)).orElse(null));
    }
}
