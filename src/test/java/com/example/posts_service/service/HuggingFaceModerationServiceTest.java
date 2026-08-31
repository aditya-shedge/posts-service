package com.example.posts_service.service;

import com.example.posts_service.dto.HuggingFaceModerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuggingFaceModerationServiceTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HuggingFaceModerationService moderationService;

    @BeforeEach
    void setUp() throws Exception {
        when(restClientBuilder.build()).thenReturn(restClient);
        moderationService = new HuggingFaceModerationService(restClientBuilder);
        setField(moderationService, "apiKey", "test-hf-key");
    }

    @Test
    void returnsFalseWhenContentIsNotHateful() {
        mockRestClientChain(buildResponse("nothate", 0.98, "hate", 0.02));

        assertFalse(moderationService.isFlagged("Class trip to science museum"));
    }

    @Test
    void returnsTrueWhenContentIsHateful() {
        mockRestClientChain(buildResponse("hate", 0.95, "nothate", 0.05));

        assertTrue(moderationService.isFlagged("Hateful content here"));
    }

    @Test
    void returnsFalseWhenHateScoreIsBelowThreshold() {
        mockRestClientChain(buildResponse("hate", 0.3, "nothate", 0.7));

        assertFalse(moderationService.isFlagged("Mildly ambiguous content"));
    }

    @Test
    void propagatesExceptionWhenApiCallFails() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Service unavailable"));

        assertThrows(org.springframework.web.client.RestClientException.class,
                () -> moderationService.isFlagged("Some text"));
    }

    private void mockRestClientChain(List<List<HuggingFaceModerationResponse.Label>> response) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(response);
    }

    private List<List<HuggingFaceModerationResponse.Label>> buildResponse(
            String label1, double score1, String label2, double score2) {
        try {
            HuggingFaceModerationResponse.Label l1 = new HuggingFaceModerationResponse.Label();
            HuggingFaceModerationResponse.Label l2 = new HuggingFaceModerationResponse.Label();
            setField(l1, "label", label1);
            setField(l1, "score", score1);
            setField(l2, "label", label2);
            setField(l2, "score", score2);
            return List.of(List.of(l1, l2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
