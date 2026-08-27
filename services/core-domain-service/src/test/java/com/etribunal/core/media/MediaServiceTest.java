package com.etribunal.core.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private CaseImageRepository imageRepository;

    @Mock
    private PresignedUrlService presignedUrlService;

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @InjectMocks
    private MediaService mediaService;

    @Test
    void requestUpload_createsImageAndReturnsPresignedUrl() {
        UUID caseId = UUID.randomUUID();
        when(imageRepository.countByCaseId(caseId)).thenReturn(0L);
        when(presignedUrlService.generateUploadUrl("image/jpeg", "photo.jpg", 1024))
                .thenReturn(new PresignedUrlService.PresignedUpload(
                        "https://s3/upload", "cases/abc.jpg", "https://s3/cases/abc.jpg", java.util.Map.of()));
        when(imageRepository.save(any(CaseImageEntity.class)))
                .thenAnswer(inv -> { CaseImageEntity e = inv.getArgument(0); e.setId(UUID.randomUUID()); return e; });

        MediaService.UploadResponse response = mediaService.requestUpload(
                caseId, "A",
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024));

        assertThat(response.uploadUrl()).isEqualTo("https://s3/upload");
        assertThat(response.storageKey()).isEqualTo("cases/abc.jpg");
        assertThat(response.imageId()).isNotNull();
    }

    @Test
    void requestUpload_throwsWhenMaxImagesReached() {
        UUID caseId = UUID.randomUUID();
        when(imageRepository.countByCaseId(caseId)).thenReturn(5L);

        assertThatThrownBy(() -> mediaService.requestUpload(
                caseId, "A",
                new MediaService.UploadRequest("image/jpeg", "photo.jpg", 1024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 5 images");
    }

    @Test
    void confirmUpload_setsDimensionsAndSendsEvent() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(UUID.randomUUID());
        entity.setStorageKey("cases/test.jpg");
        entity.setUrl("https://s3/cases/test.jpg");
        entity.setMimeType("image/jpeg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));
        when(imageRepository.save(any())).thenAnswer(inv -> { CaseImageEntity e = inv.getArgument(0); if (e.getId() == null) e.setId(imageId); return e; });

        CaseImageEntity result = mediaService.confirmUpload(imageId, 800, 600, 2048);

        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(600);
        verify(kafkaTemplate).send(eq("case-events"), any(String.class), any(byte[].class));
    }

    @Test
    void deleteImage_removesFromS3AndDb() {
        UUID imageId = UUID.randomUUID();
        CaseImageEntity entity = new CaseImageEntity();
        entity.setStorageKey("cases/test.jpg");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(entity));

        mediaService.deleteImage(imageId);

        verify(presignedUrlService).deleteObject("cases/test.jpg");
        verify(imageRepository).delete(entity);
    }
}