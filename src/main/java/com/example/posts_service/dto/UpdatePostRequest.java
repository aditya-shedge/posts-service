package com.example.posts_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {

    @NotBlank(message = "Text is required")
    private String text;

    @URL(message = "Attachment must be a valid URL")
    private String attachment;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
}
