package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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

    public AutomationScheduler(
            AutomationOrchestrator orchestrator,
            InteractionExecutor executor,
            AutomationInteractionRepository interactionRepository,
            AutomationRunRepository runRepository
    ) {
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.interactionRepository = interactionRepository;
        this.runRepository = runRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void dailyRun() {
        log.info("Daily automation run triggered");
        orchestrator.startRun(false);
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