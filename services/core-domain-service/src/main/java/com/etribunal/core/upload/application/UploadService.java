package com.etribunal.core.upload.application;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String[] ALLOWED_MIME = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;

    public UploadService(S3Client s3Client,
                         @Qualifier("s3Bucket") String bucket,
                         @Value("${etribunal.s3.endpoint:}") String endpoint) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.endpoint = endpoint != null ? endpoint : "";
    }

    public record UploadResult(String url, String publicId) {
    }

    public UploadResult uploadAvatar(MultipartFile file) {
        return upload(file, "avatars");
    }

    public UploadResult uploadImage(MultipartFile file) {
        return upload(file, "cases");
    }

    private UploadResult upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo es requerido");
        }
        String contentType = file.getContentType();
        if (contentType == null || !isAllowed(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se permiten imágenes JPEG, PNG, GIF o WebP");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo excede el límite de 5MB");
        }

        String ext = extensionFor(contentType);
        String storageKey = folder + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(contentType)
                    .build(), RequestBody.fromBytes(file.getBytes()));
            log.debug("Uploaded {} to {}/{}", contentType, bucket, storageKey);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo subir el archivo");
        }

        return new UploadResult(publicUrl(storageKey), storageKey);
    }

    private boolean isAllowed(String contentType) {
        for (String type : ALLOWED_MIME) {
            if (type.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private String publicUrl(String storageKey) {
        String base = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        if (base.isBlank()) {
            base = "http://localhost:4566";
        }
        return base + "/" + bucket + "/" + storageKey;
    }
}