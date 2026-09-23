package com.etribunal.core.media.domain;

public record MediaUploadedEvent(
    String imageId,
    String caseId,
    String storageKey,
    String url,
    String mimeType,
    Integer fileSize
) {
    public static final String EVENT_TYPE = "MediaUploaded";

    @SuppressWarnings("java:S3400")
    public String getEventType() {
        return EVENT_TYPE;
    }
}