package com.etribunal.core.media.domain;

public record MediaUploadedEvent(
    String imageId,
    String caseId,
    String storageKey,
    String url,
    String mimeType,
    Integer fileSize
) {
    public String getEventType() { return "MediaUploaded"; }
}