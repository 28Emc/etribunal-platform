package com.etribunal.core.media.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PresignedUrlServiceTest {

    @Test
    void validateContentType_throwsOnNull() {
        PresignedUrlService service = new PresignedUrlService(null, "test-bucket");
        assertThatThrownBy(() -> service.generateUploadUrl(null, "file.jpg", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content-Type is required");
    }

    @Test
    void validateContentType_throwsOnInvalidType() {
        PresignedUrlService service = new PresignedUrlService(null, "test-bucket");
        assertThatThrownBy(() -> service.generateUploadUrl("application/pdf", "file.pdf", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPEG, PNG, GIF, WebP");
    }

    @Test
    void validateFileSize_throwsOnZero() {
        PresignedUrlService service = new PresignedUrlService(null, "test-bucket");
        assertThatThrownBy(() -> service.generateUploadUrl("image/jpeg", "file.jpg", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size must be positive");
    }

    @Test
    void validateFileSize_throwsOnOversize() {
        PresignedUrlService service = new PresignedUrlService(null, "test-bucket");
        assertThatThrownBy(() -> service.generateUploadUrl("image/jpeg", "file.jpg", 6 * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB limit");
    }

    @Test
    void deleteObject_doesNotThrowWhenS3Fails() {
        PresignedUrlService service = new PresignedUrlService(null, "etribunal-media");
        assertThatCode(() -> service.deleteObject("cases/test.jpg")).doesNotThrowAnyException();
    }
}