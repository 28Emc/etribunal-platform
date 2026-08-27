package com.etribunal.ai.automation.repository;

import com.etribunal.ai.automation.domain.AutomationInteractionEntity;
import com.etribunal.ai.automation.domain.AutomationInteractionStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationInteractionRepository extends JpaRepository<AutomationInteractionEntity, UUID> {

    List<AutomationInteractionEntity> findByAutomationCaseIdAndStatus(
        UUID automationCaseId,
        AutomationInteractionStatus status
    );

    List<AutomationInteractionEntity> findByAutomationCaseIdAndPlanIndexIn(
        UUID automationCaseId,
        List<Integer> planIndexes
    );

    Optional<AutomationInteractionEntity> findByAutomationCaseIdAndPlanIndex(
        UUID automationCaseId,
        Integer planIndex
    );

    List<AutomationInteractionEntity> findByAutomationCaseIdAndInteractionTypeAndStatus(
        UUID automationCaseId,
        AutomationInteractionType type,
        AutomationInteractionStatus status
    );

    long countByAutomationCaseIdAndStatus(UUID automationCaseId, AutomationInteractionStatus status);

    long countByStatus(AutomationInteractionStatus status);

    long countByStatusAndScheduledAtLessThanEqual(AutomationInteractionStatus status, Instant scheduledAt);

    long countByStatusAndExecutedAtGreaterThanEqual(AutomationInteractionStatus status, Instant executedAt);

    List<AutomationInteractionEntity> findByStatusAndScheduledAtLessThanEqual(
        AutomationInteractionStatus status,
        Instant scheduledAt,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE AutomationInteractionEntity ai SET ai.status = :newStatus, ai.updatedAt = CURRENT_TIMESTAMP WHERE ai.status = :currentStatus AND ai.updatedAt < :staleSince")
    int expireStaleInteractions(
        @Param("currentStatus") AutomationInteractionStatus currentStatus,
        @Param("newStatus") AutomationInteractionStatus newStatus,
        @Param("staleSince") Instant staleSince
    );
}
