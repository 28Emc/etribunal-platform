package com.etribunal.core.saved;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SavedCasesController {

    private final SavedCaseService savedCaseService;
    private final CurrentUserResolver currentUser;

    public SavedCasesController(SavedCaseService savedCaseService,
                                CurrentUserResolver currentUser) {
        this.savedCaseService = savedCaseService;
        this.currentUser = currentUser;
    }

    // POST /saved-cases/{caseId}/save
    @PostMapping("/saved-cases/{caseId}/save")
    public ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> toggleSave(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.toggleSave(userId, caseId)));
    }

    // DELETE /saved-cases/{caseId}/save (unsave explícito)
    @DeleteMapping("/saved-cases/{caseId}/save")
    public ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> removeSave(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.removeSave(userId, caseId)));
    }

    // GET /saved-cases
    @GetMapping("/saved-cases")
    public ResponseEntity<ApiResponse<SavedCaseService.SavedCasesPage>> getSavedCases(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int take) {
        UUID userId = currentUser.requiredUserId(request);
        int safeTake = Math.min(Math.max(take, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.getSavedCases(userId, skip, safeTake)));
    }

    // GET /saved-cases/{caseId}/saved
    @GetMapping("/saved-cases/{caseId}/saved")
    public ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> isSaved(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.isSaved(userId, caseId)));
    }

    // POST /saved-cases/{caseId}/share
    @PostMapping("/saved-cases/{caseId}/share")
    public ResponseEntity<ApiResponse<SavedCaseService.ShareToggleResponse>> toggleShare(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.toggleShare(userId, caseId)));
    }

    // GET /saved-cases/shared
    @GetMapping("/saved-cases/shared")
    public ResponseEntity<ApiResponse<SavedCaseService.ShareCasesPage>> getSharedCases(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int take) {
        UUID userId = currentUser.requiredUserId(request);
        int safeTake = Math.min(Math.max(take, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.getSharedCases(userId, skip, safeTake)));
    }

    // GET /saved-cases/{caseId}/shared
    @GetMapping("/saved-cases/{caseId}/shared")
    public ResponseEntity<ApiResponse<SavedCaseService.ShareToggleResponse>> isShared(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.isShared(userId, caseId)));
    }

    // GET /saved-cases/{caseId}/shares
    @GetMapping("/saved-cases/{caseId}/shares")
    public ResponseEntity<ApiResponse<SavedCaseService.ShareCountResponse>> getShareCount(
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(
                savedCaseService.getShareCount(caseId)));
    }
}