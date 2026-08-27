package com.etribunal.core.media.application;

import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import com.etribunal.core.media.domain.MediaUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int MAX_IMAGES_PER_CASE = 5;

    private final CaseImageRepository imageRepository;
    private final PresignedUrlService presignedUrlService;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public MediaService(
            CaseImageRepository imageRepository,
            PresignedUrlService presignedUrlService,
            KafkaTemplate<String, byte[]> kafkaTemplate
    ) {
        this.imageRepository = imageRepository;
        this.presignedUrlService = presignedUrlService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public record UploadRequest(String contentType, String originalFilename, long fileSize) {}
    public record UploadResponse(String uploadUrl, String storageKey, String publicUrl, UUID imageId) {}

    @Transactional
    public UploadResponse requestUpload(UUID caseId, String side, UploadRequest request) {
        long count = imageRepository.countByCaseId(caseId);
        if (count >= MAX_IMAGES_PER_CASE) {
            throw new IllegalArgumentException("Maximum " + MAX_IMAGES_PER_CASE + " images per case");
        }

        PresignedUrlService.PresignedUpload presigned = presignedUrlService.generateUploadUrl(
                request.contentType(), request.originalFilename(), request.fileSize());

        CaseImageEntity entity = new CaseImageEntity();
        entity.setCaseId(caseId);
        entity.setSide(side != null ? side : "A");
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
    public CaseImageEntity confirmUpload(UUID imageId, int width, int height, long fileSize) {
        CaseImageEntity entity = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

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
    public void deleteImage(UUID imageId) {
        CaseImageEntity entity = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

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
}