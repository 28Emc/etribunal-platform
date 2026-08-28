package com.etribunal.core.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InteractionLogRepository
        extends JpaRepository<InteractionLogEntity, UUID> {

    long countByUserIdIsNotNullAndCreatedAtAfter(Instant since);

    @Query("""
            SELECT il.action, COUNT(il)
            FROM InteractionLogEntity il
            WHERE il.createdAt >= :since
            GROUP BY il.action
            """)
    List<Object[]> countByActionSince(@Param("since") Instant since);

    @Query("""
            SELECT il.action, COUNT(il)
            FROM InteractionLogEntity il
            WHERE il.caseId = :caseId
            GROUP BY il.action
            """)
    List<Object[]> countByActionForCase(@Param("caseId") UUID caseId);
}