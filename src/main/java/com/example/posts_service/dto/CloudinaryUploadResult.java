package com.example.posts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloudinaryUploadResult {
    private String url;
    private String publicId;
}
