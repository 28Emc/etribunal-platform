package com.etribunal.core.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private CaseImageRepository imageRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private PresignedUrlService presignedUrlService;

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @InjectMocks
    private MediaService mediaService;

    private UUID caseId;
    private UUID participantId;
    private UUID outsiderId;
    private CaseEntity caseEntity;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();
        caseEntity = new CaseEntity();
        caseEntity.setSideAUserId(participantId);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
    }

    @Test
    void requestUpload_createsImageAndReturnsPresignedUrl() {
        when(imageRepository.countByCaseId(caseId)).thenReturn(0L);
        when(presignedUrlService.generateUploadUrl("image/jpeg", "photo.jpg", 1024))
                .thenReturn(new PresignedUrlService.PresignedUpload(
                        "https://s3/upload", "cases/abc.jpg", "https://s3/cases/abc.jpg", java.util.Map.of()));
        when(imageRepository.save(any(CaseImageEntity.class)))
                .thenAnswer(inv -> { CaseImageEntity e = inv.getArgument(0); e.setId(UUID.randomUUID()); return e; });

        MediaService.UploadResponse response = mediaService.requestUpload(
                caseId, "A", participantId,
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024));

        assertThat(response.uploadUrl()).isEqualTo("https://s3/upload");
        assertThat(response.storageKey()).isEqualTo("cases/abc.jpg");
        assertThat(response.imageId()).isNotNull();
    }

    @Test
    void requestUpload_rejectsInvalidSide() {
        assertThatThrownBy(() -> mediaService.requestUpload(
                caseId, "X", participantId,
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }

    @Test
    void requestUpload_rejectsNonParticipant() {
        assertThatThrownBy(() -> mediaService.requestUpload(
                caseId, "A", outsiderId,
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tienes permisos");
        verify(imageRepository, never()).save(any());
    }

    @Test
    void requestUpload_throwsWhenMaxImagesReached() {
        when(imageRepository.countByCaseId(caseId)).thenReturn(5L);

        MediaService.UploadRequest request =
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024);
        assertThatThrownBy(() -> mediaService.requestUpload(caseId, "A", participantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 5 images");
    }

    @Test
    void confirmUpload_setsDimensionsAndSendsEvent() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setStorageKey("cases/test.jpg");
        entity.setUrl("https://s3/cases/test.jpg");
        entity.setMimeType("image/jpeg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));
        when(imageRepository.save(any())).thenAnswer(inv -> { CaseImageEntity e = inv.getArgument(0); if (e.getId() == null) e.setId(imageId); return e; });

        CaseImageEntity result = mediaService.confirmUpload(imageId, 800, 600, 2048, participantId);

        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(600);
        verify(kafkaTemplate).send(eq("case-events"), any(String.class), any(byte[].class));
    }

    @Test
    void confirmUpload_rejectsNonParticipant() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setStorageKey("cases/test.jpg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> mediaService.confirmUpload(imageId, 800, 600, 2048, outsiderId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void deleteImage_removesFromS3AndDb() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setStorageKey("cases/test.jpg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));

        mediaService.deleteImage(imageId, participantId);

        verify(presignedUrlService).deleteObject("cases/test.jpg");
        verify(imageRepository).delete(entity);
    }

    @Test
    void deleteImage_rejectsNonParticipant() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setStorageKey("cases/test.jpg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> mediaService.deleteImage(imageId, outsiderId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(imageRepository, never()).delete(any());
        verify(presignedUrlService, never()).deleteObject(any());
    }
}