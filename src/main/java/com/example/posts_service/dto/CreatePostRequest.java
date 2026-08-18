package com.example.posts_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public class CreatePostRequest {

    @NotBlank(message = "Text is required")
    private String text;

    @URL(message = "Attachment must be a valid URL")
    private String attachment;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;

    public CreatePostRequest() {
    }

    public CreatePostRequest(String text, String attachment, String remarks) {
        this.text = text;
        this.attachment = attachment;
        this.remarks = remarks;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
