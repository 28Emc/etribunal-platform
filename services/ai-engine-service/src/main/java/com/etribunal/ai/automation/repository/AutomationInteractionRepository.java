package com.etribunal.ai.automation.repository;

import com.etribunal.ai.automation.domain.AutomationInteractionEntity;
import com.etribunal.ai.automation.domain.AutomationInteractionStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
