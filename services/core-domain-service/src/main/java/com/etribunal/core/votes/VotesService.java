package com.etribunal.core.votes;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.notifications.NotificationService;
import com.etribunal.common.domain.notification.NotificationType;
import com.etribunal.core.votes.dto.VoteResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VotesService {

    private final VoteRepository voteRepository;
    private final CaseRepository caseRepository;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    public VotesService(VoteRepository voteRepository,
                        CaseRepository caseRepository,
                        NotificationService notificationService,
                        AnalyticsService analyticsService) {
        this.voteRepository = voteRepository;
        this.caseRepository = caseRepository;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
    }

    /**
     * Semántica de toggle heredada del monolito: mismo voto otra vez lo elimina,
     * distinto tipo lo actualiza, sin voto previo lo crea.
     */
    @Transactional
    public VoteResponse createVote(UUID caseId, UUID userId, VoteType voteType) {
        CaseEntity entity = requireCase(caseId);
        guardNotParticipant(entity, userId);
        if (entity.getStatus() != CaseStatus.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes votar en un caso que no está activo");
        }

        var existing = voteRepository.findByCaseIdAndUserId(caseId, userId);

        boolean isNewVote = existing.isEmpty();
        VoteType previous = existing.map(CaseVoteEntity::getVoteType).orElse(null);

        if (existing.isPresent()) {
            if (previous == voteType) {
                voteRepository.delete(existing.get());
                applyDeltas(caseId, voteType, -1);
                return respond(caseId, null);
            }
            existing.get().setVoteType(voteType);
            applyDeltas(caseId, previous, -1);
            applyDeltas(caseId, voteType, +1);
            analyticsService.log(InteractionAction.VOTE.name(), caseId, userId,
                    Map.of("vote_type", voteType.name(), "is_update", true));
            return respond(caseId, voteType);
        }

        CaseVoteEntity vote = new CaseVoteEntity();
        vote.setCaseId(caseId);
        vote.setUserId(userId);
        vote.setVoteType(voteType);
        voteRepository.save(vote);
        applyDeltas(caseId, voteType, +1);
        analyticsService.log(InteractionAction.VOTE.name(), caseId, userId,
                Map.of("vote_type", voteType.name(), "is_update", false));

        // Notify case author and Side B (if exists) about new vote
        if (isNewVote) {
            notifyNewVote(entity, userId, voteType);
        }

        return respond(caseId, voteType);
    }

    @Transactional
    public VoteResponse deleteVote(UUID caseId, UUID userId) {
        CaseEntity entity = requireCase(caseId);
        guardNotParticipant(entity, userId);

        voteRepository.findByCaseIdAndUserId(caseId, userId).ifPresent(vote -> {
            voteRepository.delete(vote);
            applyDeltas(caseId, vote.getVoteType(), -1);
        });

        return respond(caseId, null);
    }

    @Transactional(readOnly = true)
    public VoteResponse getVote(UUID caseId, UUID userId) {
        CaseEntity entity = requireCase(caseId);
        guardNotParticipant(entity, userId);

        return voteRepository.findByCaseIdAndUserId(caseId, userId)
                .map(CaseVoteEntity::getVoteType)
                .map(type -> respond(caseId, type))
                .orElseGet(() -> respond(caseId, null));
    }

    private void applyDeltas(UUID caseId, VoteType type, int sign) {
        int a = type == VoteType.A ? sign : 0;
        int b = type == VoteType.B ? sign : 0;
        int both = type == VoteType.BOTH_WRONG ? sign : 0;
        voteRepository.adjustVoteCounters(caseId, a, b, both);
    }

    private void guardNotParticipant(CaseEntity entity, UUID userId) {
        boolean participant = entity.getSideAUserId().equals(userId)
                || userId.equals(entity.getSideBUserId());
        if (participant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes votar en tu propio caso");
        }
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));
    }

    private VoteResponse respond(UUID caseId, VoteType voteType) {
        CaseEntity fresh = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));
        return new VoteResponse(
                caseId.toString(),
                voteType != null ? voteType.name() : null,
                fresh.getVotesA(),
                fresh.getVotesB(),
                fresh.getVotesBothWrong());
    }

    private void notifyNewVote(CaseEntity entity, UUID voterId, VoteType voteType) {
        UUID sideAId = entity.getSideAUserId();
        UUID sideBId = entity.getSideBUserId();

        Map<String, Object> payload = Map.of(
                "case_id", entity.getId().toString(),
                "vote_type", voteType.name(),
                "actor_id", voterId.toString());

        // Notify Side A (author)
        if (!sideAId.equals(voterId)) {
            notificationService.createNotification(sideAId, voterId, NotificationType.NEW_VOTE, payload);
        }

        // Notify Side B if exists and not the voter
        if (sideBId != null && !sideBId.equals(voterId)) {
            notificationService.createNotification(sideBId, voterId, NotificationType.NEW_VOTE, payload);
        }
    }
}