package com.etribunal.core.cases;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.cases.dto.CreateCaseRequest;
import com.etribunal.core.cases.dto.RespondSideBRequest;
import com.etribunal.core.cases.dto.UpdateCaseRequest;
import com.etribunal.core.config.FrontendUrlProperties;
import com.etribunal.core.moderation.ModerationService;
import com.etribunal.core.reactions.Emoji;
import com.etribunal.core.reactions.ReactionRepository;
import com.etribunal.core.reactions.ReactionTarget;
import com.etribunal.core.reports.ReportStatus;
import com.etribunal.core.saved.CaseShareRepository;
import com.etribunal.core.saved.SavedCaseRepository;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import com.etribunal.core.votes.CaseVoteEntity;
import com.etribunal.core.votes.VoteRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseService {

    public static final String MASKED_USERNAME = "Anonymous Judge";
    public static final String MASKED_AVATAR =
            "https://secure.gravatar.com/avatar/0?d=mp&f=y";

    private final CaseRepository caseRepository;
    private final InternalUsersClient usersClient;
    private final CurrentUserResolver currentUser;
    private final FrontendUrlProperties frontendUrl;
    private final SavedCaseRepository savedCaseRepository;
    private final CaseShareRepository caseShareRepository;
    private final VoteRepository voteRepository;
    private final ReactionRepository reactionRepository;
    private final ModerationService moderationService;
    private final AnalyticsService analyticsService;

    public CaseService(
            CaseRepository caseRepository,
            InternalUsersClient usersClient,
            CurrentUserResolver currentUser,
            FrontendUrlProperties frontendUrl,
            SavedCaseRepository savedCaseRepository,
            CaseShareRepository caseShareRepository,
            VoteRepository voteRepository,
            ReactionRepository reactionRepository,
            ModerationService moderationService,
            AnalyticsService analyticsService) {
        this.caseRepository = caseRepository;
        this.usersClient = usersClient;
        this.currentUser = currentUser;
        this.frontendUrl = frontendUrl;
        this.savedCaseRepository = savedCaseRepository;
        this.caseShareRepository = caseShareRepository;
        this.voteRepository = voteRepository;
        this.reactionRepository = reactionRepository;
        this.moderationService = moderationService;
        this.analyticsService = analyticsService;
    }

    // ──────────────────────── Create ────────────────────────

    @Transactional
    public CaseResponse createCase(UUID authorId, CreateCaseRequest dto) {
        boolean isVote = dto.type() == CaseType.vote;

        UUID sideBId = null;
        if (dto.sideBUserId() != null && !dto.sideBUserId().isBlank()) {
            if (!isVote) {
                throw badRequest(
                        "Solo los casos de votacion pueden asignar un usuario como Side B");
            }
            sideBId = parseUuid(dto.sideBUserId(),
                    "El usuario seleccionado para Side B no existe");
            if (sideBId.equals(authorId)) {
                throw badRequest("No puedes asignarte a ti mismo como Side B");
            }
        }

        CaseEntity entity = new CaseEntity();
        entity.setType(dto.type());
        entity.setTitle(dto.title().trim());
        entity.setSideAContent(dto.sideAContent().trim());
        entity.setCategory(dto.category() != null && !dto.category().isBlank()
                ? dto.category() : "Other");
        entity.setStatus(isVote ? CaseStatus.WAITING : CaseStatus.PUBLIC);
        entity.setAnonymous(dto.anonymousOrDefault());
        entity.setInviteToken(isVote ? UUID.randomUUID().toString() : null);
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        entity.setSideASubtitle(dto.sideASubtitle());
        entity.setSideBSubtitle(dto.sideBSubtitle());
        entity.setBothWrongSubtitle(dto.bothWrongSubtitle());

        CaseEntity saved = caseRepository.save(entity);
        moderationService.moderateCaseContentAsync(
                saved.getId(), saved.getTitle(), saved.getSideAContent(), saved.getSideBContent());
        return toResponse(List.of(saved), null)
                .getFirst();
    }

    // ──────────────────────── Feed ────────────────────────

    @Transactional(readOnly = true)
    public List<CaseResponse> getCases(int skip, int take, String feedType,
                                       String category, String q,
                                       boolean createdByMe,
                                       HttpServletRequest request) {
        Optional<UUID> currentUserId = currentUser.currentUserId(request);
        List<UUID> followingIds = null;

        boolean followingFeed = "following".equalsIgnoreCase(feedType)
                && currentUserId.isPresent();
        if (followingFeed) {
            followingIds = usersClient.followingIds(currentUserId.get());
        }

        Specification<CaseEntity> spec = CaseSpecifications.feed(
                q, category, followingIds,
                currentUserId.orElse(null), createdByMe);
        Pageable pageable = CaseSpecifications.pageable(skip, take,
                "trending".equalsIgnoreCase(feedType));

        return toResponse(caseRepository.findAll(spec, pageable).getContent(),
                currentUserId.orElse(null));
    }

    // ──────────────────────── Detail ────────────────────────

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID id, HttpServletRequest request) {
        Optional<UUID> currentUserId = currentUser.currentUserId(request);

        CaseEntity entity = caseRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        currentUserId.ifPresent(
                uid -> analyticsService.log(InteractionAction.VIEW.name(), id, uid));

        return toResponse(List.of(entity), currentUserId.orElse(null))
                .getFirst();
    }

    // ──────────────────────── Votes by user (parity legacy) ────────────────────────

    @Transactional(readOnly = true)
    public List<CaseResponse> getCasesVotedByUser(UUID userId, int skip, int take) {
        List<CaseVoteEntity> votes = voteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(skip, take));
        if (votes.isEmpty()) {
            return List.of();
        }

        List<UUID> caseIds = votes.stream().map(CaseVoteEntity::getCaseId).toList();
        Map<UUID, CaseEntity> byId = caseRepository.findAllById(caseIds).stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(CaseEntity::getId, Function.identity()));

        // Preservar el orden del voto (created_at desc del voto, igual que legacy)
        List<CaseEntity> ordered = new ArrayList<>(votes.size());
        for (CaseVoteEntity v : votes) {
            CaseEntity c = byId.get(v.getCaseId());
            if (c != null) {
                ordered.add(c);
            }
        }
        return toResponse(ordered, userId);
    }

    // ──────────────────────── Invite Token ────────────────────────

    @Transactional(readOnly = true)
    public CaseResponse getCaseByInviteToken(String token, HttpServletRequest request) {
        Optional<UUID> currentUserId = currentUser.currentUserId(request);

        CaseEntity entity = caseRepository
                .findByInviteTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invite link inválido o expirado"));

        if (entity.getType() != CaseType.vote) {
            throw badRequest("Solo los casos de votación admiten invite link");
        }

        return toResponse(List.of(entity), currentUserId.orElse(null)).getFirst();
    }

    // ──────────────────────── Respond Side B ────────────────────────

    @Transactional
    public CaseResponse respondAsSideB(UUID userId, RespondSideBRequest dto) {
        CaseEntity entity = caseRepository
                .findByInviteTokenAndDeletedAtIsNull(dto.invite_token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invite link inválido o expirado"));

        if (entity.getType() != CaseType.vote) {
            throw badRequest("Solo los casos de votación admiten respuesta de Side B");
        }
        if (entity.getStatus() != CaseStatus.WAITING) {
            throw badRequest("Este caso ya no está esperando respuesta");
        }
        if (entity.getSideAUserId().equals(userId)) {
            throw badRequest("No puedes responder tu propio caso como Side B");
        }
        if (entity.getSideBUserId() != null && !entity.getSideBUserId().equals(userId)) {
            throw badRequest("Este enlace solo puede ser utilizado por el usuario "
                    + "seleccionado para Side B");
        }
        if (entity.getSideBContent() != null) {
            throw badRequest("Este caso ya tiene una respuesta registrada");
        }

        entity.setSideBUserId(userId);
        entity.setSideBContent(dto.side_b_content().trim());
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setInviteToken(null);
        entity.setAnonymous(Boolean.TRUE.equals(dto.is_anonymous()));

        moderationService.moderateCaseContentAsync(
                entity.getId(), entity.getTitle(), entity.getSideAContent(), entity.getSideBContent());

        return toResponse(List.of(entity), userId).getFirst();
    }

    // ──────────────────────── Invite Link ────────────────────────

    @Transactional
    public InviteLinkResponse getOrRegenerateInviteLink(UUID userId, UUID caseId) {
        CaseEntity entity = caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        if (entity.getType() != CaseType.vote) {
            throw badRequest("Solo los casos de votación tienen invite link");
        }
        if (!entity.getSideAUserId().equals(userId)) {
            throw badRequest("Solo el creador del caso puede gestionar el invite link");
        }
        if (entity.getStatus() != CaseStatus.WAITING) {
            throw badRequest("El invite link solo está disponible mientras el caso "
                    + "está en WAITING");
        }

        String token = entity.getInviteToken();
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString();
            entity.setInviteToken(token);
        }
        return new InviteLinkResponse(caseId.toString(), token, frontendUrl.inviteUrl(token));
    }

    // ──────────────────────── Update Case ────────────────────────

    @Transactional
    public CaseResponse updateCase(UUID caseId, UUID userId, UpdateCaseRequest dto) {
        CaseEntity entity = caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        boolean isSideA = entity.getSideAUserId().equals(userId);
        boolean isSideB = entity.getSideBUserId() != null
                && entity.getSideBUserId().equals(userId);

        if (!isSideA && !isSideB) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permisos para editar este caso");
        }

        if (isSideA) {
            if (dto.title() != null) {
                entity.setTitle(dto.title().trim());
            }
            if (dto.side_a_content() != null) {
                entity.setSideAContent(dto.side_a_content().trim());
            }
            if (dto.side_a_subtitle() != null) {
                entity.setSideASubtitle(dto.side_a_subtitle());
            }
            if (dto.category() != null) {
                entity.setCategory(dto.category());
            }
            if (dto.is_anonymous() != null) {
                entity.setAnonymous(dto.is_anonymous());
            }
            if (dto.both_wrong_subtitle() != null) {
                entity.setBothWrongSubtitle(dto.both_wrong_subtitle());
            }
        }

        if (isSideB) {
            if (dto.side_b_content() != null) {
                entity.setSideBContent(dto.side_b_content().trim());
            }
            if (dto.side_b_subtitle() != null) {
                entity.setSideBSubtitle(dto.side_b_subtitle());
            }
        }

        moderationService.moderateCaseContentAsync(
                entity.getId(), entity.getTitle(), entity.getSideAContent(), entity.getSideBContent());

        return toResponse(List.of(entity), userId).getFirst();
    }

    // ──────────────────────── Delete Case (Moderator) ────────────────────────

    @Transactional
    public Map<String, Object> deleteCase(UUID caseId, UUID moderatorId, String reason) {
        CaseEntity entity = caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        if (entity.getReportStatus() != ReportStatus.REPORTED
                || entity.getModerationStatus() != ModerationStatus.FLAGGED) {
            throw badRequest("Solo se pueden eliminar casos que estén en revisión");
        }

        entity.setDeletedAt(java.time.Instant.now());
        caseRepository.save(entity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Caso eliminado exitosamente");
        return result;
    }

    // ──────────────────────── toResponse with enrichment ────────────────────────

    private List<CaseResponse> toResponse(List<CaseEntity> cases, UUID requesterId) {
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        List<UUID> caseIds = new ArrayList<>(cases.size());
        for (CaseEntity c : cases) {
            userIds.add(c.getSideAUserId());
            caseIds.add(c.getId());
            if (c.getSideBUserId() != null) {
                userIds.add(c.getSideBUserId());
            }
        }

        Map<UUID, UserSummary> summaries = new HashMap<>();
        if (!userIds.isEmpty()) {
            summaries.putAll(usersClient.summaries(List.copyOf(userIds)).stream()
                    .collect(Collectors.toMap(UserSummary::id, Function.identity())));
        }

        // Batch enrich: saved, shared, user_reaction, user_vote
        Set<UUID> savedIds = Set.of();
        Set<UUID> sharedIds = Set.of();
        Map<UUID, String> reactionMap = Map.of();
        Map<UUID, String> voteMap = Map.of();

        if (requesterId != null && !caseIds.isEmpty()) {
            savedIds = new HashSet<>(savedCaseRepository
                    .findCaseIdsByUserIdAndCaseIdIn(requesterId, caseIds));
            sharedIds = new HashSet<>(caseShareRepository
                    .findCaseIdsByUserIdAndCaseIdIn(requesterId, caseIds));

            List<Object[]> reactionRows = reactionRepository
                    .findEmojiByTargetTypeAndTargetIdInAndUserId(
                            ReactionTarget.CASE, caseIds, requesterId);
            reactionMap = new HashMap<>();
            for (Object[] row : reactionRows) {
                UUID targetId = (UUID) row[0];
                Emoji emoji = (Emoji) row[1];
                reactionMap.put(targetId, emoji.name());
            }

            List<CaseVoteEntity> votes = voteRepository
                    .findByUserIdAndCaseIdIn(requesterId, caseIds);
            voteMap = new HashMap<>();
            for (CaseVoteEntity v : votes) {
                voteMap.put(v.getCaseId(), v.getVoteType().name());
            }
        }

        List<CaseResponse> responses = new ArrayList<>(cases.size());
        for (CaseEntity c : cases) {
            responses.add(new CaseResponse(
                    c.getId(),
                    c.getType().name(),
                    c.getStatus().name(),
                    c.getCategory(),
                    c.getTitle(),
                    c.getSideAContent(),
                    c.getSideBContent(),
                    c.getSideASubtitle(),
                    c.getSideBSubtitle(),
                    c.getBothWrongSubtitle(),
                    c.getContentLanguage(),
                    c.isAnonymous(),
                    c.isPrivate(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getSideAUserId(),
                    c.getSideBUserId(),
                    toMaskedDto(summaries.get(c.getSideAUserId()), requesterId),
                    c.getSideBUserId() != null
                            ? toMaskedDto(summaries.get(c.getSideBUserId()), requesterId)
                            : null,
                    c.getTotalVotes(),
                    c.getVotesA(),
                    c.getVotesB(),
                    c.getVotesBothWrong(),
                    c.getTotalComments(),
                    c.getTotalViews(),
                    c.getTotalShares(),
                    c.getTotalAnchors(),
                    c.getModerationStatus().name(),
                    savedIds.contains(c.getId()),
                    sharedIds.contains(c.getId()),
                    reactionMap.get(c.getId())));
        }
        return responses;
    }

    /**
     * Réplica del maskUser del monolito: si el usuario es anónimo y no es el
     * propio requester, se oculta identidad.
     */
    private static CaseResponse.UserDto toMaskedDto(UserSummary summary,
                                                    UUID requesterId) {
        if (summary == null) {
            return new CaseResponse.UserDto(null, "Unknown", null, true);
        }
        boolean self = summary.id() != null && summary.id().equals(requesterId);
        if (summary.anonymous() && !self) {
            return new CaseResponse.UserDto(summary.id(), MASKED_USERNAME,
                    MASKED_AVATAR, true);
        }
        return CaseResponse.toUserDto(summary);
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw badRequest(message);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record InviteLinkResponse(String case_id, String invite_token,
                                     String invite_url) {
    }
}
