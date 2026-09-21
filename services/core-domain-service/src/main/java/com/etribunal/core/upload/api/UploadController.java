package com.etribunal.core.upload.api;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.upload.application.UploadService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    // Sube la imagen de un caso y devuelve su URL y public_id.
    @PostMapping("/upload/image")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        UploadService.UploadResult result = uploadService.uploadImage(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "url", result.url(),
                "public_id", result.publicId())));
    }

    // Sube el avatar del usuario y devuelve su URL.
    @PostMapping("/upload/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        UploadService.UploadResult result = uploadService.uploadAvatar(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", result.url())));
    }
}