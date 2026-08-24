package com.example.posts_service.service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.dto.UpdatePostRequest;
import com.example.posts_service.exception.InvalidPostStatusException;
import com.example.posts_service.exception.PostNotFoundException;
import com.example.posts_service.exception.UnauthorizedPostAccessException;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.model.Role;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;

    @Value("${app.attachment.temp-dir:${java.io.tmpdir}/posts-attachments}")
    private String tempDir;

    public PostService(PostRepository postRepository,
                       CloudinaryService cloudinaryService,
                       FileValidationService fileValidationService) {
        this.postRepository = postRepository;
        this.cloudinaryService = cloudinaryService;
        this.fileValidationService = fileValidationService;
    }

    public List<PostResponse> getAllPosts(UserPrincipal principal) {
        if (principal.hasRole(Role.MODERATOR)) {
            // Moderators see all DRAFT posts for review
            return postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.DRAFT).stream()
                    .map(this::toResponse)
                    .toList();
        } else {
            // Teachers see their own posts (all statuses except DELETED)
            return postRepository.findAllByCreatedByAndStatusNotOrderByCreatedAtDesc(
                            principal.getUserId(), PostStatus.DELETED).stream()
                    .map(this::toResponse)
                    .toList();
        }
    }

    public PostResponse getPostById(UUID postId, UserPrincipal principal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Check access: own post or PUBLISHED post or moderator
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException(postId);
        }

        boolean isOwner = post.getCreatedBy().equals(principal.getUserId());
        boolean isModerator = principal.hasRole(Role.MODERATOR);
        boolean isPublished = post.getStatus() == PostStatus.PUBLISHED;

        if (!isOwner && !isModerator && !isPublished) {
            throw new PostNotFoundException(postId);
        }

        return toResponse(post);
    }

    public PostResponse updatePost(UUID postId, UpdatePostRequest request, UserPrincipal principal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Treat DELETED posts as not found
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException(postId);
        }

        if (!post.getCreatedBy().equals(principal.getUserId())) {
            throw new UnauthorizedPostAccessException(postId);
        }

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new InvalidPostStatusException("Can only edit DRAFT posts");
        }

        post.setText(request.getText());
        post.setAttachment(request.getAttachment());
        post.setRemarks(request.getRemarks());

        Post saved = postRepository.save(post);
        log.info("Updated post: id={}, updatedBy={}", saved.getId(), principal.getUserId());

        return toResponse(saved);
    }

    public void deletePost(UUID postId, UserPrincipal principal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Treat DELETED posts as not found
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException(postId);
        }

        if (!post.getCreatedBy().equals(principal.getUserId())) {
            throw new UnauthorizedPostAccessException(postId);
        }

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new InvalidPostStatusException("Can only delete DRAFT posts");
        }

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);
        log.info("Deleted post: id={}, deletedBy={}", postId, principal.getUserId());
    }

    public PostResponse createPost(String text, String remarks, MultipartFile attachment, UUID userId) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setText(text);
        post.setRemarks(remarks);
        post.setStatus(PostStatus.DRAFT);
        post.setCreatedBy(userId);

        if (attachment != null && !attachment.isEmpty()) {
            fileValidationService.validate(attachment);
            post.setAttachmentFilename(attachment.getOriginalFilename());

            try {
                CloudinaryUploadResult result = cloudinaryService.upload(attachment);
                post.setAttachment(result.getUrl());
                post.setAttachmentPublicId(result.getPublicId());
                post.setAttachmentStatus(AttachmentStatus.UPLOADED);
                log.info("Attachment uploaded: postId={}, publicId={}", post.getId(), result.getPublicId());
            } catch (IOException e) {
                log.warn("Cloudinary upload failed, queuing for retry: postId={}, error={}", post.getId(), e.getMessage());
                post.setAttachmentStatus(AttachmentStatus.PENDING);
                post.setAttachmentRetryCount(0);
                post.setAttachmentNextRetryAt(LocalDateTime.now().plusMinutes(1));
                saveAttachmentToTemp(post, attachment);
            }
        }

        Post saved = postRepository.save(post);
        log.info("Created post: id={}, createdBy={}, attachmentStatus={}", saved.getId(), userId, saved.getAttachmentStatus());

        return toResponse(saved);
    }

    private void saveAttachmentToTemp(Post post, MultipartFile file) {
        try {
            Path tempDirPath = Paths.get(tempDir);
            Files.createDirectories(tempDirPath);

            String filename = post.getId() + "_" + file.getOriginalFilename();
            Path tempFile = tempDirPath.resolve(filename);
            file.transferTo(tempFile);

            post.setAttachmentTempPath(tempFile.toString());
            log.info("Saved attachment to temp: path={}", tempFile);
        } catch (IOException e) {
            log.error("Failed to save attachment to temp: postId={}, error={}", post.getId(), e.getMessage());
            post.setAttachmentStatus(AttachmentStatus.FAILED);
        }
    }

    public PostResponse approvePost(UUID postId, UserPrincipal principal) {
        if (!principal.hasRole(Role.MODERATOR)) {
            throw new UnauthorizedPostAccessException(postId);
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new InvalidPostStatusException("Can only approve DRAFT posts");
        }

        post.setStatus(PostStatus.PUBLISHED);
        Post saved = postRepository.save(post);
        log.info("Approved post: id={}, approvedBy={}", postId, principal.getUserId());

        return toResponse(saved);
    }

    public PostResponse rejectPost(UUID postId, UserPrincipal principal) {
        if (!principal.hasRole(Role.MODERATOR)) {
            throw new UnauthorizedPostAccessException(postId);
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new InvalidPostStatusException("Can only reject DRAFT posts");
        }

        post.setStatus(PostStatus.REJECTED);
        Post saved = postRepository.save(post);
        log.info("Rejected post: id={}, rejectedBy={}", postId, principal.getUserId());

        return toResponse(saved);
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getText(),
                post.getAttachment(),
                post.getAttachmentFilename(),
                post.getAttachmentStatus(),
                post.getRemarks(),
                post.getStatus(),
                post.getCreatedBy(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
