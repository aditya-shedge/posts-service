package com.example.posts_service.dto;

import com.example.posts_service.model.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private UUID id;
    private String text;
    private String attachment;
    private String remarks;
    private PostStatus status;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
