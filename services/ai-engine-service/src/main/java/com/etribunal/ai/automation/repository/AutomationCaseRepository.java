package com.etribunal.ai.automation.repository;

import com.etribunal.ai.automation.domain.AutomationCaseEntity;
import com.etribunal.ai.automation.domain.AutomationCaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationCaseRepository extends JpaRepository<AutomationCaseEntity, UUID> {

    List<AutomationCaseEntity> findByRunIdAndStatus(UUID runId, AutomationCaseStatus status);

    Optional<AutomationCaseEntity> findByRunIdAndCaseId(UUID runId, String caseId);

    Optional<AutomationCaseEntity> findByCaseId(String caseId);

    @Query("SELECT ac FROM AutomationCaseEntity ac WHERE ac.run.id = :runId ORDER BY ac.createdAt ASC")
    List<AutomationCaseEntity> findByRunIdOrderByIndex(@Param("runId") UUID runId);

    @Modifying
    @Query("UPDATE AutomationCaseEntity ac SET ac.successfulInteractions = ac.successfulInteractions + 1 WHERE ac.id = :id")
    void incrementSuccessfulInteractions(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE AutomationCaseEntity ac SET ac.failedInteractions = ac.failedInteractions + 1 WHERE ac.id = :id")
    void incrementFailedInteractions(@Param("id") UUID id);
}
