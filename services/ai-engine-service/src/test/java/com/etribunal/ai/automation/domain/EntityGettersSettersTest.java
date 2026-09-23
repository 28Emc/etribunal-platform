package com.etribunal.ai.automation.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityGettersSettersTest {

    private static void invokeLifecycle(Object entity) throws Exception {
        for (var m : entity.getClass().getDeclaredMethods()) {
            if (m.getName().equals("onCreate") || m.getName().equals("onUpdate")) {
                m.setAccessible(true);
                m.invoke(entity);
            }
        }
    }

    @Test
    void automationCaseEntity_gettersSetters() throws Exception {
        AutomationCaseEntity e = new AutomationCaseEntity();

        AutomationRunEntity run = new AutomationRunEntity();
        e.setRun(run);
        e.setCaseId("case-1");
        e.setStatus(AutomationCaseStatus.CREATED);
        e.setTargetInteractions(10);
        e.setSuccessfulInteractions(5);
        e.setFailedInteractions(2);
        e.setErrorMessage("err");
        e.setMetadata(Map.of("k", "v"));

        assertThat(e.getRun()).isEqualTo(run);
        assertThat(e.getCaseId()).isEqualTo("case-1");
        assertThat(e.getStatus()).isEqualTo(AutomationCaseStatus.CREATED);
        assertThat(e.getTargetInteractions()).isEqualTo(10);
        assertThat(e.getSuccessfulInteractions()).isEqualTo(5);
        assertThat(e.getFailedInteractions()).isEqualTo(2);
        assertThat(e.getErrorMessage()).isEqualTo("err");
        assertThat(e.getMetadata()).containsEntry("k", "v");

        invokeLifecycle(e);
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
    }

    @Test
    void automationCaseEntity_defaultStatusAndModeration() {
        AutomationCaseEntity e = new AutomationCaseEntity();
        assertThat(e.getStatus()).isEqualTo(AutomationCaseStatus.PLANNED);
        assertThat(e.getId()).isNull();
        assertThat(e.getModerationResult()).isNull();
        assertThat(e.getErrorMessage()).isNull();
        assertThat(e.getMetadata()).isNull();
    }

    @Test
    void automationCaseEntity_setModerationResult() {
        AutomationCaseEntity e = new AutomationCaseEntity();
        e.setModerationResult(com.etribunal.ai.moderation.domain.ModerationStatus.APPROVED);
        assertThat(e.getModerationResult()).isEqualTo(com.etribunal.ai.moderation.domain.ModerationStatus.APPROVED);
    }

    @Test
    void automationInteractionEntity_gettersSetters() throws Exception {
        AutomationInteractionEntity e = new AutomationInteractionEntity();
        AutomationCaseEntity c = new AutomationCaseEntity();

        e.setAutomationCase(c);
        e.setUserId("user-1");
        e.setInteractionType(AutomationInteractionType.COMMENT);
        e.setTargetId("target-1");
        e.setResultId("result-1");
        e.setStatus(AutomationInteractionStatus.SUCCESS);
        e.setStance("pro");
        e.setTone("neutral");
        e.setPlanIndex(3);
        e.setScheduledAt(Instant.now());
        e.setExecutedAt(Instant.now());
        e.setErrorMessage("err");
        e.setErrorCode("CODE");
        e.setMetadata(Map.of("content", "hi"));

        assertThat(e.getAutomationCase()).isEqualTo(c);
        assertThat(e.getUserId()).isEqualTo("user-1");
        assertThat(e.getInteractionType()).isEqualTo(AutomationInteractionType.COMMENT);
        assertThat(e.getTargetId()).isEqualTo("target-1");
        assertThat(e.getResultId()).isEqualTo("result-1");
        assertThat(e.getStatus()).isEqualTo(AutomationInteractionStatus.SUCCESS);
        assertThat(e.getStance()).isEqualTo("pro");
        assertThat(e.getTone()).isEqualTo("neutral");
        assertThat(e.getPlanIndex()).isEqualTo(3);
        assertThat(e.getScheduledAt()).isNotNull();
        assertThat(e.getExecutedAt()).isNotNull();
        assertThat(e.getErrorMessage()).isEqualTo("err");
        assertThat(e.getErrorCode()).isEqualTo("CODE");
        assertThat(e.getMetadata()).containsEntry("content", "hi");

        invokeLifecycle(e);
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
    }

    @Test
    void automationInteractionEntity_defaults() {
        AutomationInteractionEntity e = new AutomationInteractionEntity();
        assertThat(e.getStatus()).isEqualTo(AutomationInteractionStatus.PENDING);
        assertThat(e.getId()).isNull();
        assertThat(e.getPlanIndex()).isNull();
    }

    @Test
    void automationRunEntity_gettersSetters() throws Exception {
        AutomationRunEntity e = new AutomationRunEntity();

        e.setStatus(AutomationRunStatus.RUNNING);
        e.setStartedAt(Instant.now());
        e.setFinishedAt(Instant.now());
        e.setCasesRequested(5);
        e.setCasesCreated(3);
        e.setCasesFailed(1);
        e.setInteractionsPerCase(10);
        e.setInteractionIntensity(50);
        e.setDryRun(false);
        e.setMetadata(Map.of("dryRun", false));
        e.setErrorMessage("err");

        assertThat(e.getStatus()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(e.getStartedAt()).isNotNull();
        assertThat(e.getFinishedAt()).isNotNull();
        assertThat(e.getCasesRequested()).isEqualTo(5);
        assertThat(e.getCasesCreated()).isEqualTo(3);
        assertThat(e.getCasesFailed()).isEqualTo(1);
        assertThat(e.getInteractionsPerCase()).isEqualTo(10);
        assertThat(e.getInteractionIntensity()).isEqualTo(50);
        assertThat(e.isDryRun()).isFalse();
        assertThat(e.getMetadata()).containsEntry("dryRun", false);
        assertThat(e.getErrorMessage()).isEqualTo("err");

        invokeLifecycle(e);
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
    }

    @Test
    void automationRunEntity_defaults() {
        AutomationRunEntity e = new AutomationRunEntity();
        assertThat(e.getStatus()).isEqualTo(AutomationRunStatus.PENDING);
        assertThat(e.isDryRun()).isTrue();
        assertThat(e.getId()).isNull();
        assertThat(e.getStartedAt()).isNull();
        assertThat(e.getFinishedAt()).isNull();
    }
}
