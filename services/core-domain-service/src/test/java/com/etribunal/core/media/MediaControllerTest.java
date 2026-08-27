package com.etribunal.core.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.media.application.MediaService;
import com.etribunal.core.media.application.PresignedUrlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController controller;

    @Test
    void requestUpload_returnsPresignedUrl() {
        UUID caseId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(mediaService.requestUpload(any(), any(), any()))
                .thenReturn(new MediaService.UploadResponse(
                        "https://s3/upload", "cases/key.jpg", "https://s3/cases/key.jpg", imageId));

        ResponseEntity<Map<String, Object>> response = controller.requestUpload(
                caseId, "A",
                new MediaController.RequestUploadDto("image/jpeg", "photo.jpg", 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("uploadUrl");
        assertThat(response.getBody()).containsKey("imageId");
    }

    @Test
    void confirmUpload_returnsConfirmedImage() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setId(imageId);
        entity.setUrl("https://s3/cases/key.jpg");
        entity.setStorageKey("cases/key.jpg");
        entity.setWidth(800);
        entity.setHeight(600);

        when(mediaService.confirmUpload(imageId, 800, 600, 2048)).thenReturn(entity);

        ResponseEntity<Map<String, Object>> response = controller.confirmUpload(
                imageId, new MediaController.ConfirmUploadDto(800, 600, 2048));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("width")).isEqualTo(800);
    }

    @Test
    void getCaseImages_returnsList() {
        UUID caseId = UUID.randomUUID();
        when(mediaService.getCaseImages(caseId)).thenReturn(List.of());

        ResponseEntity<Object> response = controller.getCaseImages(caseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}