package com.etribunal.core.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
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
class SavedCasesControllerTest {

    @Mock
    private SavedCaseService savedCaseService;

    @Mock
    private CurrentUserResolver currentUser;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SavedCasesController controller;

    private UUID userId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        lenient().when(currentUser.requiredUserId(request)).thenReturn(userId);
    }

    @Test
    void toggleSaveDelegatesToService() {
        when(savedCaseService.toggleSave(userId, caseId))
                .thenReturn(new SavedCaseService.SavedToggleResponse(true, null));

        ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> response =
                controller.toggleSave(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().saved()).isTrue();
        verify(savedCaseService).toggleSave(userId, caseId);
    }

    @Test
    void removeSaveDelegatesToService() {
        when(savedCaseService.removeSave(userId, caseId))
                .thenReturn(new SavedCaseService.SavedToggleResponse(false, null));

        ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> response =
                controller.removeSave(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().saved()).isFalse();
        verify(savedCaseService).removeSave(userId, caseId);
    }

    @Test
    void getSavedCasesClampsTakeToOneToFifty() {
        ResponseEntity<ApiResponse<SavedCaseService.SavedCasesPage>> response =
                controller.getSavedCases(request, 0, 500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(savedCaseService).getSavedCases(userId, 0, 50);
    }

    @Test
    void isSavedDelegatesToService() {
        when(savedCaseService.isSaved(userId, caseId))
                .thenReturn(new SavedCaseService.SavedToggleResponse(true, null));

        ResponseEntity<ApiResponse<SavedCaseService.SavedToggleResponse>> response =
                controller.isSaved(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().saved()).isTrue();
        verify(savedCaseService).isSaved(userId, caseId);
    }

    @Test
    void toggleShareDelegatesToService() {
        when(savedCaseService.toggleShare(userId, caseId))
                .thenReturn(new SavedCaseService.ShareToggleResponse(true, null));

        ResponseEntity<ApiResponse<SavedCaseService.ShareToggleResponse>> response =
                controller.toggleShare(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().shared()).isTrue();
        verify(savedCaseService).toggleShare(userId, caseId);
    }

    @Test
    void getSharedCasesClampsTakeToOneToFifty() {
        ResponseEntity<ApiResponse<SavedCaseService.ShareCasesPage>> response =
                controller.getSharedCases(request, 0, 200);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(savedCaseService).getSharedCases(userId, 0, 50);
    }

    @Test
    void isSharedDelegatesToService() {
        when(savedCaseService.isShared(userId, caseId))
                .thenReturn(new SavedCaseService.ShareToggleResponse(false, null));

        ResponseEntity<ApiResponse<SavedCaseService.ShareToggleResponse>> response =
                controller.isShared(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().shared()).isFalse();
        verify(savedCaseService).isShared(userId, caseId);
    }

    @Test
    void getShareCountDelegatesToService() {
        when(savedCaseService.getShareCount(caseId))
                .thenReturn(new SavedCaseService.ShareCountResponse(3L));

        ResponseEntity<ApiResponse<SavedCaseService.ShareCountResponse>> response =
                controller.getShareCount(caseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().shares()).isEqualTo(3L);
        verify(savedCaseService).getShareCount(caseId);
    }
}