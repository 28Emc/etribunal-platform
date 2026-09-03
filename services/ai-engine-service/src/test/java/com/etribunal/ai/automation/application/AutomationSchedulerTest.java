package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@ExtendWith(MockitoExtension.class)
class AutomationSchedulerTest {

    @Mock
    private AutomationOrchestrator orchestrator;
    @Mock
    private InteractionExecutor executor;
    @Mock
    private AutomationInteractionRepository interactionRepository;
    @Mock
    private AutomationRunRepository runRepository;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private EngagementService engagementService;

    private AutomationConfig config;

    private AutomationScheduler scheduler;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        scheduler = new AutomationScheduler(
                orchestrator, executor, interactionRepository, runRepository, config, taskScheduler, engagementService
        );
    }

    @AfterEach
    void tearDown() {
        reset(orchestrator, executor, interactionRepository, runRepository, taskScheduler, engagementService);
    }

    @Test
    void scheduleDailyRun_doesNotSchedule_whenDisabled() {
        config.setEnabled(false);
        scheduler.scheduleDailyRun();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
        verify(orchestrator, never()).resumeStaleRuns();
    }

    @Test
    void scheduleDailyRun_schedulesAtConfiguredHour_whenEnabled() {
        config.setEnabled(true);
        config.setRunHour(14);

        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> {
                    Trigger trigger = inv.getArgument(1, Trigger.class);
                    assertThat(trigger).isInstanceOf(CronTrigger.class);
                    CronTrigger cron = (CronTrigger) trigger;
                    assertThat(cron.toString()).contains("0 0 14 * * *");
                    return null;
                });

        scheduler.scheduleDailyRun();

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(orchestrator).resumeStaleRuns();
    }

    @Test
    void scheduleDailyRun_resumesStaleRunsOnlyWhenEnabled() {
        config.setEnabled(false);
        scheduler.init();
        verify(orchestrator, never()).resumeStaleRuns();

        config.setEnabled(true);
        scheduler.init();
        verify(orchestrator).resumeStaleRuns();
    }

    @Test
    void dailyRun_doesNothing_whenDisabled() {
        config.setEnabled(false);
        scheduler.dailyRun();
        verify(orchestrator, never()).startRun(anyBoolean());
    }

    @Test
    void dailyRun_triggersRun_whenEnabled() {
        config.setEnabled(true);
        when(orchestrator.startRun(false)).thenReturn(
                new AutomationOrchestrator.RunResult(java.util.UUID.randomUUID(), true, "RUNNING", "/api/automation/runs/x")
        );
        scheduler.dailyRun();
        verify(orchestrator).startRun(false);
    }

    @Test
    void tick_processesDueInteractions() {
        AutomationInteractionEntity due = new AutomationInteractionEntity();
        when(interactionRepository.findByStatusAndScheduledAtLessThanEqual(
                eq(AutomationInteractionStatus.SCHEDULED), any(Instant.class), any()))
                .thenReturn(List.of(due));

        scheduler.tick();

        verify(executor).execute(null);
    }

    @Test
    void tick_expiresStaleProcessingInteractions() {
        when(interactionRepository.expireStaleInteractions(
                eq(AutomationInteractionStatus.PROCESSING),
                eq(AutomationInteractionStatus.SCHEDULED),
                any(Instant.class)))
                .thenReturn(2);
        when(interactionRepository.findByStatusAndScheduledAtLessThanEqual(
                eq(AutomationInteractionStatus.SCHEDULED), any(Instant.class), any()))
                .thenReturn(List.of());

        scheduler.tick();

        verify(interactionRepository).expireStaleInteractions(
                eq(AutomationInteractionStatus.PROCESSING),
                eq(AutomationInteractionStatus.SCHEDULED),
                any(Instant.class));
        verify(executor, never()).execute(any(UUID.class));
    }
}
