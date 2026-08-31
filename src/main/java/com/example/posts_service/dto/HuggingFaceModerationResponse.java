package com.example.posts_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from Hugging Face Inference API.
 * Returns a nested list: [[{"label": "hate", "score": 0.95}, {"label": "nothate", "score": 0.05}]]
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuggingFaceModerationResponse {

    // Outer list is batch results, inner list is labels for each input
    private List<List<Label>> results;

    public void setResults(List<List<Label>> results) {
        this.results = results;
    }

    /**
     * Returns true if the top label is "hate" with score > 0.5.
     */
    public boolean isFlagged() {
        if (results == null || results.isEmpty()) return false;
        List<Label> labels = results.get(0);
        if (labels == null || labels.isEmpty()) return false;

        return labels.stream()
                .filter(l -> "hate".equalsIgnoreCase(l.getLabel()))
                .anyMatch(l -> l.getScore() > 0.5);
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Label {
        private String label;
        private double score;
    }
}
