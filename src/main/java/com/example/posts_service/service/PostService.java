package com.example.posts_service.service;

import com.example.posts_service.dto.CloudinaryUploadResult;
import com.example.posts_service.dto.PostResponse;
import com.example.posts_service.exception.InvalidPostStatusException;
import com.example.posts_service.exception.PostNotFoundException;
import com.example.posts_service.exception.UnauthorizedPostAccessException;
import com.example.posts_service.messaging.ModerationEventProducer;
import com.example.posts_service.model.AttachmentStatus;
import com.example.posts_service.model.Post;
import com.example.posts_service.model.PostAttachment;
import com.example.posts_service.model.PostStatus;
import com.example.posts_service.model.Role;
import com.example.posts_service.repository.AttachmentRepository;
import com.example.posts_service.repository.PostRepository;
import com.example.posts_service.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final AttachmentRepository attachmentRepository;
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;
    private final ModerationEventProducer moderationEventProducer;

    @Value("${app.attachment.temp-dir:${java.io.tmpdir}/posts-attachments}")
    private String tempDir;

    public List<PostResponse> getAllPosts(UserPrincipal principal) {
        List<Post> posts = principal.hasRole(Role.MODERATOR)
                ? postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.DRAFT)
                : postRepository.findAllByCreatedByAndStatusNotOrderByCreatedAtDesc(
                        principal.getUserId(), PostStatus.DELETED);

        return posts.stream().map(this::toResponse).toList();
    }

    public PostResponse getPostById(UUID postId, UserPrincipal principal) {
        return postRepository.findVisiblePost(
                        postId,
                        principal.getUserId(),
                        principal.hasRole(Role.MODERATOR))
                .map(this::toResponse)
                .orElseThrow(() -> new PostNotFoundException(postId));
    }

    public PostResponse createPost(String text, String remarks, MultipartFile attachment, UUID userId) {
        Post post = new Post(text, remarks, userId);
        Post saved = postRepository.save(post);

        if (attachment != null && !attachment.isEmpty()) {
            fileValidationService.validate(attachment);
            PostAttachment postAttachment = new PostAttachment(
                    post.getId(), attachment.getOriginalFilename(), AttachmentStatus.PENDING, 0);

            try {
                CloudinaryUploadResult result = cloudinaryService.upload(attachment);
                postAttachment.markUploaded(result.getUrl(), result.getPublicId());
                log.info("Attachment uploaded: postId={}, publicId={}", post.getId(), result.getPublicId());
            } catch (IOException e) {
                log.warn("Cloudinary upload failed, queuing for retry: postId={}", post.getId());
                saveTempFile(postAttachment, attachment, post.getId());
            }

            attachmentRepository.save(postAttachment);
        }

        log.info("Created post: id={}, createdBy={}", saved.getId(), userId);
        moderationEventProducer.publish(saved.getId(), saved.getText());
        return toResponse(saved);
    }

    public PostResponse updatePost(UUID postId, String text, String remarks,
                                   MultipartFile attachment, boolean removeAttachment,
                                   UserPrincipal principal) {
        Post post = getActivePost(postId);
        requireOwner(post, principal.getUserId());
        requireDraft(post, "Can only edit DRAFT posts");

        post.setText(text);
        post.setRemarks(remarks);

        if (attachment != null && !attachment.isEmpty()) {
            fileValidationService.validate(attachment);
            replaceAttachment(postId, attachment);
        } else if (removeAttachment) {
            removeAttachment(postId);
        }

        log.info("Updated post: id={}, updatedBy={}", postId, principal.getUserId());
        return saveAndRespond(post);
    }

    private void replaceAttachment(UUID postId, MultipartFile file) {
        cancelPendingRetry(postId);
        deleteExistingAttachment(postId);

        PostAttachment postAttachment = new PostAttachment(
                postId, file.getOriginalFilename(), AttachmentStatus.PENDING, 0);
        try {
            CloudinaryUploadResult result = cloudinaryService.upload(file);
            postAttachment.markUploaded(result.getUrl(), result.getPublicId());
            log.info("Attachment replaced: postId={}, publicId={}", postId, result.getPublicId());
        } catch (IOException e) {
            log.warn("Cloudinary upload failed during update, queuing for retry: postId={}", postId);
            saveTempFile(postAttachment, file, postId);
        }
        attachmentRepository.save(postAttachment);
    }

    private void removeAttachment(UUID postId) {
        cancelPendingRetry(postId);
        deleteExistingAttachment(postId);
        log.info("Attachment removed: postId={}", postId);
    }

    private void deleteExistingAttachment(UUID postId) {
        attachmentRepository.findByPostId(postId).ifPresent(existing -> {
            if (existing.getPublicId() != null) {
                try {
                    cloudinaryService.delete(existing.getPublicId());
                } catch (IOException e) {
                    log.warn("Failed to delete attachment from Cloudinary: publicId={}", existing.getPublicId());
                }
            }
            attachmentRepository.delete(existing);
        });
    }

    private void cancelPendingRetry(UUID postId) {
        attachmentRepository.findByPostId(postId).ifPresent(existing -> {
            if (existing.getStatus() == AttachmentStatus.PENDING && existing.getTempPath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(existing.getTempPath()));
                    log.info("Cancelled pending retry, deleted temp file: path={}", existing.getTempPath());
                } catch (IOException e) {
                    log.warn("Failed to delete temp file during retry cancellation: path={}", existing.getTempPath());
                }
            }
        });
    }

    public void deletePost(UUID postId, UserPrincipal principal) {
        Post post = getActivePost(postId);

        requireOwner(post, principal.getUserId());
        requireDraft(post, "Can only delete DRAFT posts");
        setStatus(post, PostStatus.DELETED);

        postRepository.save(post);
        log.info("Deleted post: id={}, deletedBy={}", postId, principal.getUserId());
    }

    public PostResponse approvePost(UUID postId, UserPrincipal principal) {
        requireModerator(principal, postId);

        Post post = getActivePost(postId);

        requireDraft(post, "Can only approve DRAFT posts");
        setStatus(post, PostStatus.PUBLISHED);

        log.info("Approved post: id={}, approvedBy={}", postId, principal.getUserId());
        return saveAndRespond(post);
    }

    public PostResponse rejectPost(UUID postId, UserPrincipal principal) {
        requireModerator(principal, postId);

        Post post = getActivePost(postId);

        requireDraft(post, "Can only reject DRAFT posts");
        setStatus(post, PostStatus.REJECTED);

        log.info("Rejected post: id={}, rejectedBy={}", postId, principal.getUserId());
        return saveAndRespond(post);
    }

    private Post getActivePost(UUID postId) {
        return postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new PostNotFoundException(postId));
    }

    private void requireOwner(Post post, UUID userId) {
        if (!post.getCreatedBy().equals(userId)) {
            throw new UnauthorizedPostAccessException(post.getId());
        }
    }

    private void requireDraft(Post post, String errorMessage) {
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new InvalidPostStatusException(errorMessage);
        }
    }

    private void requireModerator(UserPrincipal principal, UUID postId) {
        if (!principal.hasRole(Role.MODERATOR)) {
            throw new UnauthorizedPostAccessException(postId);
        }
    }

    private PostResponse saveAndRespond(Post post) {
        return toResponse(postRepository.save(post));
    }

    private void setStatus(Post post, PostStatus status) {
        post.setStatus(status);
    }

    private void saveTempFile(PostAttachment attachment, MultipartFile file, UUID postId) {
        try {
            Path tempDirPath = Paths.get(tempDir);
            Files.createDirectories(tempDirPath);

            String filename = postId + "_" + file.getOriginalFilename();
            Path tempFile = tempDirPath.resolve(filename);
            file.transferTo(tempFile);

            attachment.setTempPath(tempFile.toString());
            log.info("Saved attachment to temp: path={}", tempFile);
        } catch (IOException e) {
            log.error("Failed to save attachment to temp: postId={}, error={}", postId, e.getMessage());
            attachment.setStatus(AttachmentStatus.FAILED);
        }
    }

    private PostResponse toResponse(Post post) {
        PostAttachment attachment = attachmentRepository.findByPostId(post.getId()).orElse(null);
        return new PostResponse(
                post.getId(),
                post.getText(),
                attachment != null ? attachment.getUrl() : null,
                attachment != null ? attachment.getFilename() : null,
                attachment != null ? attachment.getStatus() : null,
                post.getRemarks(),
                post.getStatus(),
                post.getModerationStatus(),
                post.getModerationReason(),
                post.getCreatedBy(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
