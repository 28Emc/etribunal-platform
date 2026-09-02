package com.etribunal.core.media.api;

import com.etribunal.core.media.application.MediaService;
import com.etribunal.core.media.application.PresignedUrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    public record RequestUploadDto(
        @NotBlank String contentType,
        String originalFilename,
        @Positive @Max(5242880) long fileSize
    ) {}

    public record ConfirmUploadDto(
        @Min(1) int width,
        @Min(1) int height,
        @Positive long fileSize
    ) {}

    @PostMapping("/cases/{caseId}/images/upload-url")
    public ResponseEntity<Map<String, Object>> requestUpload(
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "A") String side,
            @Valid @RequestBody RequestUploadDto dto
    ) {
        MediaService.UploadResponse response = mediaService.requestUpload(
                caseId, side,
                new MediaService.UploadRequest(dto.contentType(), dto.originalFilename(), dto.fileSize()));

        return ResponseEntity.ok(Map.of(
                "uploadUrl", response.uploadUrl(),
                "storageKey", response.storageKey(),
                "publicUrl", response.publicUrl(),
                "imageId", response.imageId().toString()
        ));
    }

    @PostMapping("/images/{imageId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(
            @PathVariable UUID imageId,
            @Valid @RequestBody ConfirmUploadDto dto
    ) {
        var entity = mediaService.confirmUpload(imageId, dto.width(), dto.height(), dto.fileSize());

        return ResponseEntity.ok(Map.of(
                "id", entity.getId().toString(),
                "url", entity.getUrl(),
                "storageKey", entity.getStorageKey(),
                "width", entity.getWidth(),
                "height", entity.getHeight()
        ));
    }

    @GetMapping("/cases/{caseId}/images")
    public ResponseEntity<Object> getCaseImages(@PathVariable UUID caseId) {
        return ResponseEntity.ok(mediaService.getCaseImages(caseId));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Map<String, String>> deleteImage(@PathVariable UUID imageId) {
        mediaService.deleteImage(imageId);
        return ResponseEntity.ok(Map.of("message", "Image deleted"));
    }
}