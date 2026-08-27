package com.etribunal.core.media.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class PresignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(PresignedUrlService.class);
    private static final Duration UPLOAD_EXPIRATION = Duration.ofMinutes(10);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final S3Presigner presigner;
    private final String bucket;

    public PresignedUrlService(S3Presigner presigner, @Qualifier("s3Bucket") String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public record PresignedUpload(
        String uploadUrl,
        String storageKey,
        String publicUrl,
        Map<String, java.util.List<String>> fields
    ) {}

    public PresignedUpload generateUploadUrl(String contentType, String originalFilename, long fileSize) {
        validateContentType(contentType);
        validateFileSize(fileSize);

        String ext = extractExtension(originalFilename);
        String storageKey = "cases/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_EXPIRATION)
                        .putObjectRequest(putRequest)
                        .build());

        String publicUrl = endpointUrl() + "/" + bucket + "/" + storageKey;

        return new PresignedUpload(
                presignedRequest.url().toString(),
                storageKey,
                publicUrl,
                presignedRequest.signedHeaders()
        );
    }

    public void deleteObject(String storageKey) {
        try {
            var s3 = software.amazon.awssdk.services.s3.S3Client.builder().build();
            s3.deleteObject(b -> b.bucket(bucket).key(storageKey));
            log.debug("Deleted object: {}", storageKey);
        } catch (Exception e) {
            log.error("Failed to delete S3 object {}: {}", storageKey, e.getMessage());
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content-Type is required");
        }
        String[] allowed = {"image/jpeg", "image/png", "image/gif", "image/webp"};
        boolean valid = false;
        for (String type : allowed) {
            if (type.equals(contentType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException("Only JPEG, PNG, GIF, WebP images are allowed");
        }
    }

    private void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String endpointUrl() {
        // For local dev (Floci/LocalStack), return the endpoint
        // In production, this would be the CloudFront or S3 URL
        return "http://localhost:4566";
    }
}