package com.etribunal.core.saved;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedCaseService {

    private final SavedCaseRepository savedCaseRepository;
    private final CaseShareRepository caseShareRepository;
    private final CaseRepository caseRepository;
    private final InternalUsersClient usersClient;
    private final AnalyticsService analyticsService;

    public SavedCaseService(SavedCaseRepository savedCaseRepository,
                            CaseShareRepository caseShareRepository,
                            CaseRepository caseRepository,
                            InternalUsersClient usersClient,
                            AnalyticsService analyticsService) {
        this.savedCaseRepository = savedCaseRepository;
        this.caseShareRepository = caseShareRepository;
        this.caseRepository = caseRepository;
        this.usersClient = usersClient;
        this.analyticsService = analyticsService;
    }

    /**
     * Toggle save: si ya guardado -> quita; si no -> guarda.
     * Devuelve { saved: true/false, case: SavedCaseResponse | null }
     */
    @Transactional
    public SavedToggleResponse toggleSave(UUID userId, UUID caseId) {
        CaseEntity caseEntity = requireCase(caseId);

        Optional<SavedCaseEntity> existing = savedCaseRepository.findByUserIdAndCaseId(userId, caseId);

        if (existing.isPresent()) {
            // Unsave
            savedCaseRepository.delete(existing.get());
            caseRepository.adjustShareCounter(caseId, -1);
            return new SavedToggleResponse(false, null);
        }

        // Save
        SavedCaseEntity saved = new SavedCaseEntity();
        saved.setUserId(userId);
        saved.setCaseId(caseId);
        saved.setCreatedAt(Instant.now());
        savedCaseRepository.save(saved);
        caseRepository.adjustShareCounter(caseId, 1);
        analyticsService.log(InteractionAction.SAVE.name(), caseId, userId);

        return new SavedToggleResponse(true, toSavedCaseResponse(caseEntity, Instant.now()));
    }

    @Transactional(readOnly = true)
    public SavedCasesPage getSavedCases(UUID userId, int skip, int take) {
        List<SavedCaseEntity> saved = savedCaseRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<SavedCaseEntity> page = saved.stream()
                .skip(skip)
                .limit(take)
                .toList();

        List<UUID> caseIds = page.stream().map(SavedCaseEntity::getCaseId).toList();
        List<CaseEntity> cases = caseRepository.findAllById(caseIds);
        Map<UUID, CaseEntity> caseMap = cases.stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(CaseEntity::getId, Function.identity()));

        List<SavedCaseResponse> responses = new ArrayList<>();
        for (SavedCaseEntity sc : page) {
            CaseEntity c = caseMap.get(sc.getCaseId());
            if (c != null) {
                responses.add(toSavedCaseResponse(c, sc.getCreatedAt()));
            }
        }

        long total = savedCaseRepository.countByUserIdAndCaseDeletedAtIsNull(userId);
        return new SavedCasesPage(responses, total);
    }

    @Transactional(readOnly = true)
    public SavedToggleResponse isSaved(UUID userId, UUID caseId) {
        boolean saved = savedCaseRepository.findByUserIdAndCaseId(userId, caseId).isPresent();
        return new SavedToggleResponse(saved, null);
    }

    /**
     * Unsave explícito: si existe el guardado lo elimina y ajusta el contador.
     * Devuelve { saved: false }.
     */
    @Transactional
    public SavedToggleResponse removeSave(UUID userId, UUID caseId) {
        requireCase(caseId);
        int deleted = savedCaseRepository.deleteByUserIdAndCaseId(userId, caseId);
        if (deleted > 0) {
            caseRepository.adjustShareCounter(caseId, -1);
        }
        return new SavedToggleResponse(false, null);
    }

    /**
     * Toggle share: si ya compartido -> quita; si no -> comparte.
     * Devuelve { shared: true/false, case: CaseShareResponse | null }
     */
    @Transactional
    public ShareToggleResponse toggleShare(UUID userId, UUID caseId) {
        CaseEntity caseEntity = requireCase(caseId);

        Optional<CaseShareEntity> existing = caseShareRepository.findByUserIdAndCaseId(userId, caseId);

        if (existing.isPresent()) {
            // Unshare
            caseShareRepository.delete(existing.get());
            caseRepository.adjustShareCounter(caseId, -1);
            return new ShareToggleResponse(false, null);
        }

        // Share
        CaseShareEntity share = new CaseShareEntity();
        share.setUserId(userId);
        share.setCaseId(caseId);
        share.setCreatedAt(Instant.now());
        caseShareRepository.save(share);
        caseRepository.adjustShareCounter(caseId, 1);
        analyticsService.log(InteractionAction.SHARE.name(), caseId, userId);

        return new ShareToggleResponse(true, toShareResponse(caseEntity, Instant.now()));
    }

    @Transactional(readOnly = true)
    public ShareCasesPage getSharedCases(UUID userId, int skip, int take) {
        List<CaseShareEntity> shares = caseShareRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<CaseShareEntity> page = shares.stream()
                .skip(skip)
                .limit(take)
                .toList();

        List<UUID> caseIds = page.stream().map(CaseShareEntity::getCaseId).toList();
        List<CaseEntity> cases = caseRepository.findAllById(caseIds);
        Map<UUID, CaseEntity> caseMap = cases.stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(CaseEntity::getId, Function.identity()));

        List<CaseShareResponse> responses = new ArrayList<>();
        for (CaseShareEntity sc : page) {
            CaseEntity c = caseMap.get(sc.getCaseId());
            if (c != null) {
                responses.add(toShareResponse(c, sc.getCreatedAt()));
            }
        }

        long total = caseShareRepository.countByUserIdAndCaseDeletedAtIsNull(userId);
        return new ShareCasesPage(responses, total);
    }

    @Transactional(readOnly = true)
    public ShareToggleResponse isShared(UUID userId, UUID caseId) {
        boolean shared = caseShareRepository.findByUserIdAndCaseId(userId, caseId).isPresent();
        return new ShareToggleResponse(shared, null);
    }

    @Transactional(readOnly = true)
    public ShareCountResponse getShareCount(UUID caseId) {
        requireCase(caseId);
        long count = caseShareRepository.countByCaseId(caseId);
        return new ShareCountResponse(count);
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));
    }

    private SavedCaseResponse toSavedCaseResponse(CaseEntity c, Instant savedAt) {
        return new SavedCaseResponse(
                c.getId().toString(),
                c.getId().toString(),
                c.getTitle(),
                c.getCategory(),
                c.getType().name().toLowerCase(),
                c.getStatus().name(),
                savedAt,
                c.getSideAUserId(),
                c.getSideBUserId(),
                null, // side_a_user - se enriquece en controller si necesario
                null, // side_b_user
                c.getSideAContent(),
                c.getSideBContent(),
                c.getSideASubtitle(),
                c.getSideBSubtitle(),
                c.getBothWrongSubtitle(),
                c.getVotesA(),
                c.getVotesB(),
                c.getVotesBothWrong(),
                List.of(),
                c.isAnonymous(),
                c.getModerationStatus().name(),
                "NONE", // report_status - no existe en entidad, default legacy
                c.getTotalShares(),
                c.getTotalShares(),
                c.getTotalComments(),
                c.getTotalComments(),
                true,
                false,
                null,
                new SavedCaseResponse.ReactionsSummary(
                        new SavedCaseResponse.ReactionsSummary.Counts(0, 0, 0))
        );
    }

    private CaseShareResponse toShareResponse(CaseEntity c, Instant sharedAt) {
        return new CaseShareResponse(
                c.getId().toString(),
                c.getTitle(),
                c.getCategory(),
                c.getCreatedAt(),
                sharedAt,
                null, // side_a_username
                null  // side_a_avatar
        );
    }

    // --- Response Records (snake_case para compatibilidad legacy) ---

    public record SavedToggleResponse(boolean saved, SavedCaseResponse caseResponse) {
    }

    public record SavedCasesPage(List<SavedCaseResponse> cases, long total) {
    }

    public record ShareToggleResponse(boolean shared, CaseShareResponse caseResponse) {
    }

    public record ShareCasesPage(List<CaseShareResponse> cases, long total) {
    }

    public record ShareCountResponse(long shares) {
    }

    public record SavedCaseResponse(
            String case_id, String id, String title, String category, String type,
            String status, Instant created_at, UUID side_a_user_id, UUID side_b_user_id,
            UserDto side_a_user, UserDto side_b_user, String side_a_content,
            String side_b_content, String side_a_subtitle, String side_b_subtitle,
            String both_wrong_subtitle, long votes_a, long votes_b, long votes_both_wrong,
            List<ImageDto> images, boolean is_anonymous, String moderation_status,
            String report_status, long total_anchors, long total_shares, long total_comments,
            long comments_count, boolean is_saved, boolean is_shared, String user_reaction,
            ReactionsSummary reactions_summary
    ) {
        public record UserDto(String id, String username, String avatar_url, boolean is_anonymous) {
        }

        public record ImageDto(String id, String url, String side) {
        }

        public record ReactionsSummary(Counts counts) {
            public record Counts(long LIKE, long LOVE, long ANGRY) {
            }
        }
    }

    public record CaseShareResponse(
            String case_id, String title, String category, Instant created_at,
            Instant shared_at, String side_a_username, String side_a_avatar
    ) {
    }
}