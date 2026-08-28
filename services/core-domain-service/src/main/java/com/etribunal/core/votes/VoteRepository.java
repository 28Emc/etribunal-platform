package com.etribunal.core.votes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<CaseVoteEntity, UUID> {

    Optional<CaseVoteEntity> findByCaseIdAndUserId(UUID caseId, UUID userId);

    List<CaseVoteEntity> findByUserIdAndCaseIdIn(UUID userId, List<UUID> caseIds);

    List<CaseVoteEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Ajusta los contadores del caso de forma atómica: decrementa el voto previo,
     * incrementa el nuevo y recalcula total_votes en una sola UPDATE.
     * clearAutomatically evita leer entidades obsoletas del persistence context
     * después del bulk update.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CaseEntity c SET
                c.votesA = c.votesA + :deltaA,
                c.votesB = c.votesB + :deltaB,
                c.votesBothWrong = c.votesBothWrong + :deltaBothWrong,
                c.totalVotes = c.votesA + c.votesB + c.votesBothWrong
                    + :deltaA + :deltaB + :deltaBothWrong,
                c.updatedAt = CURRENT_TIMESTAMP
            WHERE c.id = :caseId
            """)
    int adjustVoteCounters(@Param("caseId") UUID caseId,
                           @Param("deltaA") int deltaA,
                           @Param("deltaB") int deltaB,
                           @Param("deltaBothWrong") int deltaBothWrong);
}
