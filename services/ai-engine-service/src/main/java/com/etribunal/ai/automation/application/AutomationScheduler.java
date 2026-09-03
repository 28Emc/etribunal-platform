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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
    private static final int TICK_BATCH = 5;
    private static final long PROCESSING_STALE_MS = 10 * 60 * 1000;

    private final AutomationOrchestrator orchestrator;
    private final InteractionExecutor executor;
    private final AutomationInteractionRepository interactionRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationConfig config;
    private final TaskScheduler taskScheduler;
    private final EngagementService engagementService;
    private final ActivityProfileService activityProfileService;

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
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.interactionRepository = interactionRepository;
        this.runRepository = runRepository;
        this.config = config;
        this.taskScheduler = taskScheduler;
        this.engagementService = engagementService;
        this.activityProfileService = activityProfileService;
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
        }
    }

    private void processDueInteractions() {
        Instant now = Instant.now();
        List<AutomationInteractionEntity> due = interactionRepository.findByStatusAndScheduledAtLessThanEqual(
                AutomationInteractionStatus.SCHEDULED, now, org.springframework.data.domain.PageRequest.of(0, TICK_BATCH)
        );

        for (AutomationInteractionEntity interaction : due) {
            try {
                executor.execute(interaction.getId());
            } catch (Exception e) {
                log.error("Failed to execute interaction {}: {}", interaction.getId(), e.getMessage());
            }
        }
    }
}