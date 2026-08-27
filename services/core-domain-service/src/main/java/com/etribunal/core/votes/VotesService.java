package com.etribunal.core.votes;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.votes.dto.VoteResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VotesService {

    private final VoteRepository voteRepository;
    private final CaseRepository caseRepository;

    public VotesService(VoteRepository voteRepository, CaseRepository caseRepository) {
        this.voteRepository = voteRepository;
        this.caseRepository = caseRepository;
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

        if (existing.isPresent()) {
            VoteType previous = existing.get().getVoteType();
            if (previous == voteType) {
                voteRepository.delete(existing.get());
                applyDeltas(caseId, voteType, -1);
                return respond(caseId, null);
            }
            existing.get().setVoteType(voteType);
            applyDeltas(caseId, previous, -1);
            applyDeltas(caseId, voteType, +1);
            return respond(caseId, voteType);
        }

        CaseVoteEntity vote = new CaseVoteEntity();
        vote.setCaseId(caseId);
        vote.setUserId(userId);
        vote.setVoteType(voteType);
        voteRepository.save(vote);
        applyDeltas(caseId, voteType, +1);

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
}
