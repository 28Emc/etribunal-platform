package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AutomationInteractionEntity;
import com.etribunal.ai.automation.domain.AutomationInteractionStatus;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

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
    @Mock
    private ActivityProfileService activityProfileService;
    @Mock
    private Clock clock;

    @Spy
    private AutomationConfig config = new AutomationConfig();

    @InjectMocks
    private AutomationScheduler scheduler;

    @BeforeEach
    void setUp() {
        config.setEnabled(true);
        config.setRunHour(9);
        config.getActivity().setEnabled(true);
        config.getEngagement().setEnabled(true);
        config.getEngagement().setEvaluationDays(7);

        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-03T10:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(interactionRepository.expireStaleInteractions(any(), any(), any())).thenReturn(0);
        lenient().when(interactionRepository.findByStatusAndScheduledAtLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(runRepository.existsByCreatedAtAfter(any())).thenReturn(false);
        lenient().when(engagementService.getAnalyticsSummary(anyInt())).thenReturn(java.util.Map.of());
        lenient().when(engagementService.evaluateRecentCases(anyInt())).thenReturn(0);
    }

    @Test
    void scheduleDailyRun_schedulesCronTask_whenEnabled() {
        scheduler.scheduleDailyRun();

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(orchestrator).resumeStaleRuns();
    }

    @Test
    void scheduleDailyRun_doesNotSchedule_whenDisabled() {
        config.setEnabled(false);

        scheduler.scheduleDailyRun();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void dailyRun_startsRunAndRefreshesProfile_whenEnabled() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-03T10:00:00Z"));

        scheduler.dailyRun();

        verify(orchestrator).startRun(false);
        verify(activityProfileService).refresh();
        verify(engagementService).evaluateRecentCases(anyInt());
    }

    @Test
    void dailyRun_doesNothing_whenDisabled() {
        config.setEnabled(false);

        scheduler.dailyRun();

        verify(orchestrator, never()).startRun(anyBoolean());
        verify(activityProfileService, never()).refresh();
    }

    @Test
    void tick_expiresStaleInteractionsAndProcessesDue() {
        lenient().when(interactionRepository.expireStaleInteractions(any(), any(), any())).thenReturn(2);
        lenient().when(interactionRepository.findByStatusAndScheduledAtLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        scheduler.tick();

        verify(interactionRepository).expireStaleInteractions(any(), any(), any());
        verify(interactionRepository).findByStatusAndScheduledAtLessThanEqual(any(), any(), any());
        verify(orchestrator).broadcastQueueStatus();
    }

    @Test
    void tick_broadcastsQueueStatus_whenExpiredInteractions() {
        lenient().when(interactionRepository.expireStaleInteractions(any(), any(), any())).thenReturn(3);
        lenient().when(interactionRepository.findByStatusAndScheduledAtLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        scheduler.tick();

        verify(orchestrator).broadcastQueueStatus();
    }

    @Test
    void init_resumesStaleRuns_whenEnabled() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-03T10:00:00Z"));

        scheduler.init();

        verify(orchestrator).resumeStaleRuns();
    }

    @Test
    void init_skips_whenDisabled() {
        config.setEnabled(false);

        scheduler.init();

        verify(orchestrator, never()).resumeStaleRuns();
    }
}