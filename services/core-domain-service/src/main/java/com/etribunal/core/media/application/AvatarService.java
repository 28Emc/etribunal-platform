package com.etribunal.core.media.application;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Sube avatares directamente a S3 y devuelve la URL pública.
 * Endpoint: POST /upload/avatar (ruteado por el gateway a core-domain).
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;
    private final String publicEndpoint;

    public AvatarService(
            S3Client s3Client,
            @Qualifier("s3Bucket") String bucket,
            @Value("${etribunal.s3.endpoint:http://localhost:4566}") String endpoint,
            @Value("${etribunal.s3.public-endpoint:}") String publicEndpoint) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
    }

    public record AvatarUpload(String url) {}

    public AvatarUpload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona una imagen para el avatar");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Solo se permiten imágenes JPEG, PNG, GIF o WebP");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "La imagen supera el límite de 5MB");
        }

        String ext = extractExtension(file.getOriginalFilename());
        String storageKey =
                "avatars/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.debug("Avatar subido: {}/{}", bucket, storageKey);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen", e);
        }

        return new AvatarUpload(publicUrl(storageKey));
    }

    private String publicUrl(String storageKey) {
        // Path-style, igual que PresignedUrlService (LocalStack/Floci en dev).
        // La base pública es la que ve el navegador (public-endpoint), NO el
        // endpoint del cliente S3 (que en Docker apunta al hostname interno).
        return resolvePublicBase() + "/" + bucket + "/" + storageKey;
    }

    private String resolvePublicBase() {
        String base = publicEndpoint;
        if (base == null || base.isBlank()) {
            base = endpoint;
        }
        if (base == null || base.isBlank()) {
            base = "http://localhost:4566";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}