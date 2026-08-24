package com.example.posts_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.posts_service.dto.CloudinaryUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public CloudinaryUploadResult upload(MultipartFile file) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap(
                "folder", "posts",
                "resource_type", "auto"
        );

        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);

        String url = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");

        log.info("Uploaded file to Cloudinary: publicId={}, url={}", publicId, url);

        return new CloudinaryUploadResult(url, publicId);
    }

    public void delete(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        log.info("Deleted file from Cloudinary: publicId={}", publicId);
    }
}
