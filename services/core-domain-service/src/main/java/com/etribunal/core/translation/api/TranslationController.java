package com.etribunal.core.translation.api;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.translation.application.TranslationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    // POST /translations/cases/{caseId} → { id, sourceLanguage, targetLanguage, title, sideA, ... }
    @PostMapping("/translations/cases/{caseId}")
    public ResponseEntity<ApiResponse<TranslationService.TranslatedCaseResponse>> translateCase(
            @PathVariable UUID caseId,
            @RequestBody(required = false) Map<String, ?> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                translationService.translateCase(caseId, extractLanguage(body))));
    }

    // POST /translations/comments/{commentId} → { id, commentId, sourceLanguage, targetLanguage, content }
    @PostMapping("/translations/comments/{commentId}")
    public ResponseEntity<ApiResponse<TranslationService.TranslatedCommentResponse>> translateComment(
            @PathVariable UUID commentId,
            @RequestBody(required = false) Map<String, ?> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                translationService.translateComment(commentId, extractLanguage(body))));
    }

    private static String extractLanguage(Map<String, ?> body) {
        if (body == null) {
            return null;
        }
        Object raw = body.get("targetLanguage") != null
                ? body.get("targetLanguage")
                : body.get("target_language");
        return raw instanceof String s ? s : null;
    }
}