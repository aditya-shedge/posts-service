package com.example.posts_service.service;

import com.example.posts_service.dto.HuggingFaceModerationRequest;
import com.example.posts_service.dto.HuggingFaceModerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class HuggingFaceModerationService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceModerationService.class);

    private static final String MODEL_URL =
            "https://router.huggingface.co/hf-inference/models/facebook/roberta-hate-speech-dynabench-r4-target/pipeline/text-classification";

    private final RestClient restClient;

    @Value("${app.huggingface.api-key}")
    private String apiKey;

    public HuggingFaceModerationService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public boolean isFlagged(String text) {
        HuggingFaceModerationRequest request = new HuggingFaceModerationRequest(text);

        // HF returns: [[{"label":"hate","score":0.95},{"label":"nothate","score":0.05}]]
        List<List<HuggingFaceModerationResponse.Label>> response = restClient.post()
                .uri(MODEL_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.isEmpty() || response.get(0).isEmpty()) {
            log.warn("Empty response from Hugging Face moderation API, defaulting to approved");
            return false;
        }

        boolean flagged = response.get(0).stream()
                .filter(l -> "hate".equalsIgnoreCase(l.getLabel()))
                .anyMatch(l -> l.getScore() > 0.5);

        log.info("Hugging Face moderation result: flagged={}", flagged);
        return flagged;
    }
}
