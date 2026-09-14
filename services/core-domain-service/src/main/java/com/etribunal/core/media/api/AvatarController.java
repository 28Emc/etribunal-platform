package com.etribunal.core.media.api;

import com.etribunal.core.media.application.AvatarService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint de avatar del perfil. El gateway rutea /api/upload/** hacia
 * core-domain-service, así que este controller vive bajo /upload.
 */
@RestController
@RequestMapping("/upload")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        AvatarService.AvatarUpload upload = avatarService.upload(file);
        return ResponseEntity.ok(Map.of("url", upload.url()));
    }
}