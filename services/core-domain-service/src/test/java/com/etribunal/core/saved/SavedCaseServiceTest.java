package com.etribunal.core.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.CaseType;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedCaseServiceTest {

    @Mock
    private SavedCaseRepository savedCaseRepository;

    @Mock
    private CaseShareRepository caseShareRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private InternalUsersClient usersClient;

    @Mock
    private AnalyticsService analyticsService;

    private SavedCaseService savedCaseService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        savedCaseService = new SavedCaseService(savedCaseRepository, caseShareRepository,
                caseRepository, usersClient, analyticsService);
        lenient().when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(publicCase()));
    }

    @Test
    void saveCaseCreatesRecordAndIncrementsCounter() {
        when(savedCaseRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.empty());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(publicCase()));

        var response = savedCaseService.toggleSave(userId, caseId);

        assertThat(response.saved()).isTrue();
        assertThat(response.caseResponse()).isNotNull();

        ArgumentCaptor<SavedCaseEntity> captor =
                ArgumentCaptor.forClass(SavedCaseEntity.class);
        verify(savedCaseRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getCaseId()).isEqualTo(caseId);
        verify(caseRepository).adjustShareCounter(caseId, 1);
    }

    @Test
    void unsaveCaseRemovesRecordAndDecrementsCounter() {
        SavedCaseEntity existing = new SavedCaseEntity();
        existing.setUserId(userId);
        existing.setCaseId(caseId);
        when(savedCaseRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.of(existing));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(publicCase()));

        var response = savedCaseService.toggleSave(userId, caseId);

        assertThat(response.saved()).isFalse();
        assertThat(response.caseResponse()).isNull();
        verify(savedCaseRepository).delete(existing);
        verify(caseRepository).adjustShareCounter(caseId, -1);
    }

    @Test
    void shareCaseCreatesRecordAndIncrementsCounter() {
        when(caseShareRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.empty());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(publicCase()));

        var response = savedCaseService.toggleShare(userId, caseId);

        assertThat(response.shared()).isTrue();
        assertThat(response.caseResponse()).isNotNull();

        ArgumentCaptor<CaseShareEntity> captor =
                ArgumentCaptor.forClass(CaseShareEntity.class);
        verify(caseShareRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getCaseId()).isEqualTo(caseId);
        verify(caseRepository).adjustShareCounter(caseId, 1);
    }

    @Test
    void unshareCaseRemovesRecordAndDecrementsCounter() {
        CaseShareEntity existing = new CaseShareEntity();
        existing.setUserId(userId);
        existing.setCaseId(caseId);
        when(caseShareRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.of(existing));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(publicCase()));

        var response = savedCaseService.toggleShare(userId, caseId);

        assertThat(response.shared()).isFalse();
        assertThat(response.caseResponse()).isNull();
        verify(caseShareRepository).delete(existing);
        verify(caseRepository).adjustShareCounter(caseId, -1);
    }

    @Test
    void getSavedCasesReturnsPaginatedResults() {
        SavedCaseEntity sc1 = new SavedCaseEntity();
        sc1.setUserId(userId);
        sc1.setCaseId(UUID.randomUUID());
        sc1.setCreatedAt(Instant.now());
        SavedCaseEntity sc2 = new SavedCaseEntity();
        sc2.setUserId(userId);
        sc2.setCaseId(UUID.randomUUID());
        sc2.setCreatedAt(Instant.now().minusSeconds(60));
        when(savedCaseRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(sc1, sc2));
        when(caseRepository.findAllById(any()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream().map(id -> {
                        CaseEntity c = publicCase();
                        setField(c, "id", id);
                        return c;
                    }).toList();
                });
        when(savedCaseRepository.countByUserIdAndCaseDeletedAtIsNull(userId)).thenReturn(2L);

        var page = savedCaseService.getSavedCases(userId, 0, 10);

        assertThat(page.cases()).hasSize(2);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void getSavedCasesRespectsSkipAndTake() {
        SavedCaseEntity sc1 = new SavedCaseEntity();
        sc1.setUserId(userId);
        sc1.setCaseId(UUID.randomUUID());
        sc1.setCreatedAt(Instant.now());
        SavedCaseEntity sc2 = new SavedCaseEntity();
        sc2.setUserId(userId);
        sc2.setCaseId(UUID.randomUUID());
        sc2.setCreatedAt(Instant.now().minusSeconds(60));
        SavedCaseEntity sc3 = new SavedCaseEntity();
        sc3.setUserId(userId);
        sc3.setCaseId(UUID.randomUUID());
        sc3.setCreatedAt(Instant.now().minusSeconds(120));
        when(savedCaseRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(sc1, sc2, sc3));
        when(caseRepository.findAllById(any()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream().map(id -> {
                        CaseEntity c = publicCase();
                        setField(c, "id", id);
                        return c;
                    }).toList();
                });
        when(savedCaseRepository.countByUserIdAndCaseDeletedAtIsNull(userId)).thenReturn(3L);

        var page = savedCaseService.getSavedCases(userId, 1, 1);

        assertThat(page.cases()).hasSize(1);
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void getSharedCasesReturnsPaginatedResults() {
        CaseShareEntity cs1 = new CaseShareEntity();
        cs1.setUserId(userId);
        cs1.setCaseId(UUID.randomUUID());
        cs1.setCreatedAt(Instant.now());
        when(caseShareRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(cs1));
        when(caseRepository.findAllById(any()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream().map(id -> {
                        CaseEntity c = publicCase();
                        setField(c, "id", id);
                        return c;
                    }).toList();
                });
        when(caseShareRepository.countByUserIdAndCaseDeletedAtIsNull(userId)).thenReturn(1L);

        var page = savedCaseService.getSharedCases(userId, 0, 10);

        assertThat(page.cases()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void isSavedReturnsTrueWhenExists() {
        when(savedCaseRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.of(new SavedCaseEntity()));

        var response = savedCaseService.isSaved(userId, caseId);

        assertThat(response.saved()).isTrue();
    }

    @Test
    void isSharedReturnsFalseWhenNotExists() {
        when(caseShareRepository.findByUserIdAndCaseId(userId, caseId))
                .thenReturn(Optional.empty());

        var response = savedCaseService.isShared(userId, caseId);

        assertThat(response.shared()).isFalse();
    }

    @Test
    void getShareCountReturnsCount() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(publicCase()));
        when(caseShareRepository.countByCaseId(caseId)).thenReturn(5L);

        var response = savedCaseService.getShareCount(caseId);

        assertThat(response.shares()).isEqualTo(5);
    }

    @Test
    void saveOnMissingCaseThrowsNotFound() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedCaseService.toggleSave(userId, caseId))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(savedCaseRepository, never()).save(any());
    }

    @Test
    void shareOnMissingCaseThrowsNotFound() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedCaseService.toggleShare(userId, caseId))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(caseShareRepository, never()).save(any());
    }

    private CaseEntity publicCase() {
        CaseEntity entity = new CaseEntity();
        entity.setType(CaseType.classic);
        entity.setTitle("Caso");
        entity.setSideAContent("A");
        entity.setSideAUserId(userId);
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setCategory("general");
        setField(entity, "id", caseId);
        return entity;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}