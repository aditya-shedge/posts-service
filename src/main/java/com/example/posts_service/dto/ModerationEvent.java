package com.example.posts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ModerationEvent {
    private UUID postId;
    private String text;
    private LocalDateTime createdAt;
}
