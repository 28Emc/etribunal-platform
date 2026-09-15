package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
    private static final int TICK_BATCH = 5;
    private static final long PROCESSING_STALE_MS = 10L * 60 * 1000;

    private final AutomationOrchestrator orchestrator;
    private final InteractionExecutor executor;
    private final AutomationInteractionRepository interactionRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationConfig config;
    private final TaskScheduler taskScheduler;
    private final EngagementService engagementService;
    private final ActivityProfileService activityProfileService;
    private final Clock clock;

    @Autowired
    public AutomationScheduler(
            AutomationOrchestrator orchestrator,
            InteractionExecutor executor,
            AutomationInteractionRepository interactionRepository,
            AutomationRunRepository runRepository,
            AutomationConfig config,
            TaskScheduler taskScheduler,
            EngagementService engagementService,
            ActivityProfileService activityProfileService
    ) {
        this(orchestrator, executor, interactionRepository, runRepository, config, taskScheduler,
                engagementService, activityProfileService, Clock.systemDefaultZone());
    }

    public AutomationScheduler(
            AutomationOrchestrator orchestrator,
            InteractionExecutor executor,
            AutomationInteractionRepository interactionRepository,
            AutomationRunRepository runRepository,
            AutomationConfig config,
            TaskScheduler taskScheduler,
            EngagementService engagementService,
            ActivityProfileService activityProfileService,
            Clock clock
    ) {
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.interactionRepository = interactionRepository;
        this.runRepository = runRepository;
        this.config = config;
        this.taskScheduler = taskScheduler;
        this.engagementService = engagementService;
        this.activityProfileService = activityProfileService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleDailyRun() {
        init();
        if (!config.isEnabled()) {
            log.info("Automation disabled (AI_ENABLED=false), no daily run scheduled");
            return;
        }
        int hour = Math.max(0, Math.min(23, config.getRunHour()));
        String cron = "0 0 " + hour + " * * *";
        taskScheduler.schedule(this::dailyRun, new CronTrigger(cron));
        log.info("Daily automation run scheduled at {}:00 ({})", hour, cron);

        catchUpMissedRun(hour);
    }

    private void catchUpMissedRun(int runHour) {
        int currentHour = LocalTime.now(clock).getHour();
        if (currentHour < runHour) {
            log.debug("Catch-up not needed: current hour {} < runHour {}", currentHour, runHour);
            return;
        }
        Instant todayStart = Instant.now(clock).atZone(ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        if (runRepository.existsByCreatedAtAfter(todayStart)) {
            log.info("Catch-up skipped: a run already exists for today");
            return;
        }
        log.info("Catch-up triggered: current hour {} >= runHour {}, no run today -> starting run", currentHour, runHour);
        orchestrator.startRun(false);
    }

    public void dailyRun() {
        if (!config.isEnabled()) {
            return;
        }
        log.info("Daily automation run triggered");
        if (config.getActivity().isEnabled()) {
            activityProfileService.refresh();
        }
        orchestrator.startRun(false);
        evaluateEngagement();
    }

    public void evaluateEngagement() {
        if (!config.getEngagement().isEnabled()) {
            return;
        }
        int evaluated = engagementService.evaluateRecentCases(config.getEngagement().getEvaluationDays());
        log.info("Engagement evaluation completed for {} cases", evaluated);
        orchestrator.broadcastEngagement(engagementService.getAnalyticsSummary(10));
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void tick() {
        try {
            expireStaleProcessing();
            processDueInteractions();
        } catch (Exception e) {
            log.error("Tick error: {}", e.getMessage());
        }
    }

    public void init() {
        if (!config.isEnabled()) {
            log.info("AutomationScheduler disabled (AI_ENABLED=false), skipping stale-run resume");
            return;
        }
        log.info("AutomationScheduler initializing, resuming stale runs...");
        orchestrator.resumeStaleRuns();
    }

    private void expireStaleProcessing() {
        Instant staleSince = Instant.now().minusMillis(PROCESSING_STALE_MS);
        int expired = interactionRepository.expireStaleInteractions(
                AutomationInteractionStatus.PROCESSING,
                AutomationInteractionStatus.SCHEDULED,
                staleSince
        );
        if (expired > 0) {
            log.warn("Expired {} stale PROCESSING interactions back to SCHEDULED", expired);
            orchestrator.broadcastQueueStatus();
        }
    }

    private void processDueInteractions() {
        Instant now = Instant.now();
        List<AutomationInteractionEntity> due = interactionRepository.findByStatusAndScheduledAtLessThanEqual(
                AutomationInteractionStatus.SCHEDULED, now, org.springframework.data.domain.PageRequest.of(0, TICK_BATCH)
        );

        int executed = 0;
        for (AutomationInteractionEntity interaction : due) {
            try {
                InteractionExecutor.ExecuteResult result = executor.execute(interaction.getId());
                if (result != null && result.status() != null) {
                    executed++;
                }
            } catch (Exception e) {
                log.error("Failed to execute interaction {}: {}", interaction.getId(), e.getMessage());
            }
        }

        if (executed > 0) {
            orchestrator.broadcastQueueStatus();
            log.info("Tick executed {} interactions (queue updated)", executed);
        }
    }
}