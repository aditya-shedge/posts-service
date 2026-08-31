package com.example.posts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HuggingFaceModerationRequest {
    private String inputs;
}
