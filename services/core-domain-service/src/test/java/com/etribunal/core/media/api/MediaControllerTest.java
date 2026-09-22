package com.etribunal.core.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.media.application.MediaService;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @Mock
    private CurrentUserResolver currentUser;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private MediaController controller;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        lenient().when(currentUser.requiredUserId(request)).thenReturn(userId);
    }

    @Test
    void requestUpload_returnsPresignedUrl() {
        UUID caseId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(mediaService.requestUpload(any(), any(), any(), any()))
                .thenReturn(new MediaService.UploadResponse(
                        "https://s3/upload", "cases/key.jpg", "https://s3/cases/key.jpg", imageId));

        ResponseEntity<Map<String, Object>> response = controller.requestUpload(
                caseId, "A",
                new MediaController.RequestUploadDto("image/jpeg", "photo.jpg", 1024),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("uploadUrl");
        assertThat(response.getBody()).containsKey("imageId");
        verify(mediaService).requestUpload(eq(caseId), eq("A"), eq(userId), any());
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

        when(mediaService.confirmUpload(imageId, 800, 600, 2048, userId)).thenReturn(entity);

        ResponseEntity<Map<String, Object>> response = controller.confirmUpload(
                imageId, new MediaController.ConfirmUploadDto(800, 600, 2048), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("width", 800);
    }

    @Test
    void getCaseImages_returnsList() {
        UUID caseId = UUID.randomUUID();
        when(mediaService.getCaseImages(caseId)).thenReturn(List.of());

        ResponseEntity<Object> response = controller.getCaseImages(caseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteImage_requiresAuthenticatedUser() {
        UUID imageId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = controller.deleteImage(imageId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mediaService).deleteImage(imageId, userId);
    }

    @Test
    void endpointsRequireUserId() {
        when(currentUser.requiredUserId(request))
                .thenThrow(new IllegalStateException("Usuario no autenticado"));

        UUID caseId = UUID.randomUUID();
        MediaController.RequestUploadDto dto =
                new MediaController.RequestUploadDto("image/jpeg", "photo.jpg", 1024);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.requestUpload(caseId, "A", dto, request))
                .isInstanceOf(IllegalStateException.class);

        verify(mediaService, never()).requestUpload(any(), any(), any(), any());
        verifyNoMoreInteractions(mediaService);
    }
}