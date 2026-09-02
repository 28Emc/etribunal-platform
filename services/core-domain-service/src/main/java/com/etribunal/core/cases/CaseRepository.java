package com.etribunal.core.cases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseRepository
        extends JpaRepository<CaseEntity, UUID>, JpaSpecificationExecutor<CaseEntity> {

    Optional<CaseEntity> findByInviteTokenAndDeletedAtIsNull(String inviteToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CaseEntity c SET c.totalComments = c.totalComments + :delta, "
            + "c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :caseId")
    int adjustCommentCounter(@Param("caseId") UUID caseId,
                             @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CaseEntity c SET c.totalShares = c.totalShares + :delta, "
            + "c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :caseId")
    int adjustShareCounter(@Param("caseId") UUID caseId,
                           @Param("delta") int delta);

    // Trending: casos públicos con más interacciones (votos + comentarios + shares + anchors)
    @Query("SELECT c FROM CaseEntity c WHERE c.deletedAt IS NULL AND c.status = 'PUBLIC' "
            + "ORDER BY (c.totalVotes + c.totalComments + c.totalShares + c.totalAnchors) DESC, c.createdAt DESC")
    Page<CaseEntity> findTrendingCases(Pageable pageable);

    // Active users: usuarios con más casos creados (públicos)
    @Query("SELECT c.sideAUserId, COUNT(c) FROM CaseEntity c WHERE c.deletedAt IS NULL AND c.status = 'PUBLIC' "
            + "GROUP BY c.sideAUserId ORDER BY COUNT(c) DESC")
    List<Object[]> countCasesBySideAUser(Pageable pageable);
}
