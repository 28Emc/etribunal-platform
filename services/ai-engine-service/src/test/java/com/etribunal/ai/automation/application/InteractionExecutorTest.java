package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class InteractionExecutorTest {

    @Mock
    private AutomationInteractionRepository interactionRepository;
    @Mock
    private AutomationCaseRepository caseRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private InteractionExecutor executor;

    @Test
    void execute_returnsFailed_whenInteractionNotFound() {
        when(interactionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void execute_returnsSuccess_whenAlreadyCompleted() {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setStatus(AutomationInteractionStatus.SUCCESS);
        entity.setResultId("existing-result");

        when(interactionRepository.findById(any(UUID.class))).thenReturn(Optional.of(entity));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.resultId()).isEqualTo("existing-result");
    }

    @Test
    void scheduleInteractions_createsScheduledEntities() {
        UUID caseId = UUID.randomUUID();
        AutomationCaseEntity caseEntity = new AutomationCaseEntity();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        List<InteractionPlanner.PlannedInteractionWithUser> planned = List.of(
                new InteractionPlanner.PlannedInteractionWithUser(0, AutomationInteractionType.COMMENT, "u1", "pro-A", 50, "Content", null, null, null),
                new InteractionPlanner.PlannedInteractionWithUser(1, AutomationInteractionType.VOTE, "u2", null, null, null, null, "A", null)
        );

        when(interactionRepository.save(any(AutomationInteractionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<AutomationInteractionEntity> result = executor.scheduleInteractions(
                caseId, planned, Instant.now(), 30, 180, 24
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getInteractionType()).isEqualTo(AutomationInteractionType.COMMENT);
        assertThat(result.get(0).getStatus()).isEqualTo(AutomationInteractionStatus.SCHEDULED);
        assertThat(result.get(1).getInteractionType()).isEqualTo(AutomationInteractionType.VOTE);
    }
}