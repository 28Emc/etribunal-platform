package com.etribunal.ai.automation.repository;

import com.etribunal.ai.automation.domain.AutomationRunEntity;
import com.etribunal.ai.automation.domain.AutomationRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationRunRepository extends JpaRepository<AutomationRunEntity, UUID> {

    Optional<AutomationRunEntity> findFirstByStatusInAndCreatedAtAfter(
        List<AutomationRunStatus> statuses,
        Instant since
    );

    @Query("SELECT r FROM AutomationRunEntity r WHERE r.status IN :statuses AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<AutomationRunEntity> findActiveRunsSince(
        @Param("statuses") List<AutomationRunStatus> statuses,
        @Param("since") Instant since
    );

    @Query("SELECT r FROM AutomationRunEntity r ORDER BY r.createdAt DESC")
    List<AutomationRunEntity> findRecentRuns();

    Optional<AutomationRunEntity> findFirstByStatusIn(List<AutomationRunStatus> statuses);
}
