package com.etribunal.core.media.application;

import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import com.etribunal.core.media.domain.MediaUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int MAX_IMAGES_PER_CASE = 5;

    private final CaseImageRepository imageRepository;
    private final CaseRepository caseRepository;
    private final PresignedUrlService presignedUrlService;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public MediaService(
            CaseImageRepository imageRepository,
            CaseRepository caseRepository,
            PresignedUrlService presignedUrlService,
            KafkaTemplate<String, byte[]> kafkaTemplate
    ) {
        this.imageRepository = imageRepository;
        this.caseRepository = caseRepository;
        this.presignedUrlService = presignedUrlService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public record UploadRequest(String contentType, String originalFilename, long fileSize) {}
    public record UploadResponse(String uploadUrl, String storageKey, String publicUrl, UUID imageId) {}

    @Transactional
    public UploadResponse requestUpload(UUID caseId, String side, UUID userId, UploadRequest request) {
        if (side == null || (!"A".equals(side) && !"B".equals(side))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El parámetro side debe ser 'A' o 'B'");
        }
        if (!isParticipant(caseId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permisos para subir imágenes a este caso");
        }

        long count = imageRepository.countByCaseId(caseId);
        if (count >= MAX_IMAGES_PER_CASE) {
            throw new IllegalArgumentException("Maximum " + MAX_IMAGES_PER_CASE + " images per case");
        }

        PresignedUrlService.PresignedUpload presigned = presignedUrlService.generateUploadUrl(
                request.contentType(), request.originalFilename(), request.fileSize());

        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setSide(side);
        entity.setUrl(presigned.publicUrl());
        entity.setStorageKey(presigned.storageKey());
        entity.setOriginalFilename(request.originalFilename());
        entity.setMimeType(request.contentType());
        entity.setOrderIndex((int) count);
        entity = imageRepository.save(entity);

        log.debug("Upload requested for case {} image {}: {}", caseId, entity.getId(), presigned.storageKey());

        return new UploadResponse(
                presigned.uploadUrl(),
                presigned.storageKey(),
                presigned.publicUrl(),
                entity.getId()
        );
    }

    @Transactional
    public CaseImageEntity confirmUpload(UUID imageId, int width, int height, long fileSize, UUID userId) {
        CaseImageEntity entity = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Image not found: " + imageId));

        if (!isParticipant(entity.getCaseId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permisos sobre esta imagen");
        }

        entity.setWidth(width);
        entity.setHeight(height);
        entity.setFileSize((int) fileSize);

        CaseImageEntity saved = imageRepository.save(entity);

        MediaUploadedEvent event = new MediaUploadedEvent(
                entity.getId().toString(),
                entity.getCaseId().toString(),
                entity.getStorageKey(),
                entity.getUrl(),
                entity.getMimeType(),
                entity.getFileSize()
        );

        try {
            byte[] bytes = com.etribunal.common.kafka.EventJson.mapper().writeValueAsBytes(event);
            kafkaTemplate.send("case-events", entity.getCaseId().toString(), bytes);
        } catch (Exception e) {
            log.error("Failed to send MediaUploaded event: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CaseImageEntity> getCaseImages(UUID caseId) {
        return imageRepository.findByCaseIdOrderByOrderIndexAsc(caseId);
    }

    @Transactional
    public void deleteImage(UUID imageId, UUID userId) {
        CaseImageEntity entity = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Image not found: " + imageId));

        if (!isParticipant(entity.getCaseId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permisos para borrar esta imagen");
        }

        presignedUrlService.deleteObject(entity.getStorageKey());
        imageRepository.delete(entity);

        log.debug("Image deleted: {} from case {}", imageId, entity.getCaseId());
    }

    @Transactional
    public void deleteAllCaseImages(UUID caseId) {
        List<CaseImageEntity> images = imageRepository.findByCaseIdOrderByOrderIndexAsc(caseId);
        for (CaseImageEntity img : images) {
            presignedUrlService.deleteObject(img.getStorageKey());
        }
        imageRepository.deleteAll(images);
    }

    private boolean isParticipant(UUID caseId, UUID userId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .map(c -> c.getSideAUserId().equals(userId)
                        || (c.getSideBUserId() != null
                                && c.getSideBUserId().equals(userId)))
                .orElse(false);
    }
}