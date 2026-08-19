package com.example.posts_service.controller;

import com.example.posts_service.dto.CreatePostRequest;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.dto.UpdatePostRequest;
import com.example.posts_service.security.UserPrincipal;
import com.example.posts_service.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.createPost(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PostResponse response = postService.updatePost(postId, request, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
