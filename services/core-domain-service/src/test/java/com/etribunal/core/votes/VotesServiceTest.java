package com.etribunal.core.votes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.CaseType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VotesServiceTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private CaseRepository caseRepository;

    private VotesService votesService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID voterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        votesService = new VotesService(voteRepository, caseRepository);
        lenient().when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(publicCase()));
    }

    @Test
    void newVoteIncrementsCounter() {
        when(voteRepository.findByCaseIdAndUserId(caseId, voterId))
                .thenReturn(Optional.empty());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                publicCase(1, 0, 0)));

        var response = votesService.createVote(caseId, voterId,
                com.etribunal.core.votes.VoteType.A);

        assertThat(response.vote_type()).isEqualTo("A");
        ArgumentCaptor<CaseVoteEntity> captor =
                ArgumentCaptor.forClass(CaseVoteEntity.class);
        verify(voteRepository).save(captor.capture());
        assertThat(captor.getValue().getVoteType()).isEqualTo(VoteType.A);
        verify(voteRepository).adjustVoteCounters(caseId, 1, 0, 0);
    }

    @Test
    void sameVoteAgainRemovesIt() {
        CaseVoteEntity existing = new CaseVoteEntity();
        existing.setCaseId(caseId);
        existing.setUserId(voterId);
        existing.setVoteType(VoteType.A);
        when(voteRepository.findByCaseIdAndUserId(caseId, voterId))
                .thenReturn(Optional.of(existing));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                publicCase(0, 0, 0)));

        var response = votesService.createVote(caseId, voterId,
                com.etribunal.core.votes.VoteType.A);

        assertThat(response.vote_type()).isNull();
        verify(voteRepository).delete(existing);
        verify(voteRepository).adjustVoteCounters(caseId, -1, 0, 0);
        verify(voteRepository, never()).save(any());
    }

    @Test
    void switchingVoteAdjustsBothCounters() {
        CaseVoteEntity existing = new CaseVoteEntity();
        existing.setCaseId(caseId);
        existing.setUserId(voterId);
        existing.setVoteType(VoteType.A);
        when(voteRepository.findByCaseIdAndUserId(caseId, voterId))
                .thenReturn(Optional.of(existing));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                publicCase(0, 1, 0)));

        var response = votesService.createVote(caseId, voterId,
                com.etribunal.core.votes.VoteType.BOTH_WRONG);

        assertThat(response.vote_type()).isEqualTo("BOTH_WRONG");
        assertThat(existing.getVoteType()).isEqualTo(VoteType.BOTH_WRONG);
        verify(voteRepository).adjustVoteCounters(caseId, -1, 0, 0);
        verify(voteRepository).adjustVoteCounters(caseId, 0, 0, 1);
    }

    @Test
    void authorCannotVoteOwnCase() {
        assertThatThrownBy(() -> votesService.createVote(caseId, authorId,
                com.etribunal.core.votes.VoteType.A))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("No puedes votar");
        verify(voteRepository, never()).save(any());
    }

    @Test
    void sideBParticipantCannotVote() {
        UUID sideB = UUID.randomUUID();
        CaseEntity entity = publicCase();
        entity.setSideBUserId(sideB);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> votesService.createVote(caseId, sideB,
                com.etribunal.core.votes.VoteType.B))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void waitingCaseRejectsVotes() {
        CaseEntity waiting = publicCase();
        waiting.setStatus(CaseStatus.WAITING);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> votesService.createVote(caseId, voterId,
                com.etribunal.core.votes.VoteType.B))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("no está activo");
    }

    @Test
    void invalidVoteTypeRejectedByMapper() {
        assertThatThrownBy(() -> VoteRequestMapper.toVoteType(
                new com.etribunal.core.votes.dto.CreateVoteRequest("SIDE_A")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("BOTH_WRONG");
    }

    @Test
    void deleteVoteRemovesAndDecrements() {
        CaseVoteEntity existing = new CaseVoteEntity();
        existing.setCaseId(caseId);
        existing.setUserId(voterId);
        existing.setVoteType(VoteType.B);
        when(voteRepository.findByCaseIdAndUserId(caseId, voterId))
                .thenReturn(Optional.of(existing));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                publicCase(0, 0, 0)));

        var response = votesService.deleteVote(caseId, voterId);

        assertThat(response.vote_type()).isNull();
        verify(voteRepository).delete(existing);
        verify(voteRepository).adjustVoteCounters(caseId, 0, -1, 0);
    }

    @Test
    void getVoteReturnsNullWhenNoVote() {
        when(voteRepository.findByCaseIdAndUserId(eq(caseId), eq(voterId)))
                .thenReturn(Optional.empty());

        var response = votesService.getVote(caseId, voterId);

        assertThat(response.vote_type()).isNull();
    }

    private CaseEntity publicCase() {
        return publicCase(0, 0, 0);
    }

    private CaseEntity publicCase(int a, int b, int both) {
        CaseEntity entity = new CaseEntity();
        entity.setType(CaseType.vote);
        entity.setTitle("Caso de votacion de prueba");
        entity.setSideAContent("Contenido de prueba del caso");
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setSideAUserId(authorId);
        entity.setVotesA(a);
        entity.setVotesB(b);
        entity.setVotesBothWrong(both);
        entity.setTotalVotes(a + b + both);
        return entity;
    }
}
