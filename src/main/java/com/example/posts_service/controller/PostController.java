package com.example.posts_service.controller;

import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.security.UserPrincipal;
import com.example.posts_service.service.PostService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@Validated
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<Page<PostResponse>> getAllPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(postService.getAllPosts(principal, pageable));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(postService.getPostById(postId, principal));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("text") @NotBlank(message = "Text is required") String text,
            @RequestParam(value = "remarks", required = false)
                @Size(max = 1000, message = "Remarks must not exceed 1000 characters") String remarks,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.createPost(text, remarks, attachment, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @RequestParam("text") @NotBlank(message = "Text is required") String text,
            @RequestParam(value = "remarks", required = false)
                @Size(max = 1000, message = "Remarks must not exceed 1000 characters") String remarks,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            @RequestParam(value = "removeAttachment", required = false, defaultValue = "false")
                boolean removeAttachment,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.updatePost(
                postId, text, remarks, attachment, removeAttachment, principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        postService.deletePost(postId, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<PostResponse> approvePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.approvePost(postId, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/reject")
    public ResponseEntity<PostResponse> rejectPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.rejectPost(postId, principal);
        return ResponseEntity.ok(response);
    }
}
