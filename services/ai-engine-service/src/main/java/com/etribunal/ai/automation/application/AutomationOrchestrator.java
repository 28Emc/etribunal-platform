package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AutomationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AutomationOrchestrator.class);
    private static final long STALE_MS = 30 * 60 * 1000;

    private final AutomationConfig config;
    private final AutomationRunRepository runRepository;
    private final AutomationCaseRepository caseRepository;
    private final AutomationInteractionRepository interactionRepository;
    private final CaseGenerator caseGenerator;
    private final UserSelector userSelector;
    private final JdbcTemplate jdbcTemplate;

    public AutomationOrchestrator(
            AutomationConfig config,
            AutomationRunRepository runRepository,
            AutomationCaseRepository caseRepository,
            AutomationInteractionRepository interactionRepository,
            CaseGenerator caseGenerator,
            UserSelector userSelector,
            JdbcTemplate jdbcTemplate
    ) {
        this.config = config;
        this.runRepository = runRepository;
        this.caseRepository = caseRepository;
        this.interactionRepository = interactionRepository;
        this.caseGenerator = caseGenerator;
        this.userSelector = userSelector;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RunResult(UUID runId, boolean started, String status, String pollingUrl) {}

    @Transactional
    public RunResult startRun(boolean dryRunOverride) {
        Instant todayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        Optional<AutomationRunEntity> activeRun = runRepository.findFirstByStatusInAndCreatedAtAfter(
                List.of(AutomationRunStatus.PENDING, AutomationRunStatus.RUNNING), todayStart);

        if (activeRun.isPresent()) {
            AutomationRunEntity existing = activeRun.get();
            if (existing.getStartedAt() != null &&
                Instant.now().toEpochMilli() - existing.getStartedAt().toEpochMilli() > STALE_MS) {
                existing.setStatus(AutomationRunStatus.FAILED);
                existing.setErrorMessage("Stale recovery");
                existing.setFinishedAt(Instant.now());
                runRepository.save(existing);
            } else {
                return new RunResult(existing.getId(), false, existing.getStatus().name(),
                        "/automation/runs/" + existing.getId());
            }
        }

        boolean dryRun = config.isDryRun();
        if (dryRunOverride && !config.isDryRun()) {
            dryRun = dryRunOverride;
        }

        int dailyCases = config.pickDailyCases();
        int usersPerCase = config.pickUsersPerCase();
        int intensity = config.pickIntensity();
        int maxPerUser = config.pickMaxPerUser();
        int schedulingInterval = config.pickSchedulingInterval();

        AutomationRunEntity run = new AutomationRunEntity();
        run.setStatus(AutomationRunStatus.PENDING);
        run.setDryRun(dryRun);
        run.setCasesRequested(dailyCases);
        run.setInteractionsPerCase(usersPerCase);
        run.setInteractionIntensity(intensity);
        run.setMetadata(Map.of(
                "options", Map.of(
                        "dryRun", dryRun,
                        "dailyCases", dailyCases,
                        "usersPerCase", usersPerCase,
                        "intensity", intensity,
                        "maxPerUser", maxPerUser,
                        "schedulingInterval", schedulingInterval
                )
        ));
        run = runRepository.save(run);

        UUID runId = run.getId();
        log.info("Run {} created (dryRun={}, cases={}, users/case={})", runId, dryRun, dailyCases, usersPerCase);

        launchRun(runId, dryRun, dailyCases, usersPerCase, intensity, maxPerUser, schedulingInterval);

        return new RunResult(runId, true, "RUNNING", "/automation/runs/" + runId);
    }

    @Async
    public void launchRun(
            UUID runId, boolean dryRun, int dailyCases,
            int usersPerCase, int intensity, int maxPerUser, int schedulingInterval
    ) {
        try {
            AutomationRunEntity run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomationRunStatus.RUNNING);
            run.setStartedAt(Instant.now());
            runRepository.save(run);

            int poolSize = config.getDailyPoolSize() > 0
                    ? config.getDailyPoolSize()
                    : (int) Math.ceil((double) dailyCases * usersPerCase * maxPerUser / maxPerUser);
            List<UserSelector.BotUser> pool = userSelector.selectDailyPool(poolSize);

            if (pool.isEmpty()) {
                log.warn("No eligible bot users found. Auto-enabling seed users...");
                autoEnableBots();
                pool = userSelector.selectDailyPool(poolSize);
            }

            List<String> recentTopics = getRecentTopics();

            int casesCreated = 0;
            int casesFailed = 0;

            for (int i = 0; i < dailyCases; i++) {
                try {
                    CaseGenerator.CaseResult result = caseGenerator.generateCase(
                            runId, i, recentTopics, pool, dryRun).block();

                    if (result != null && result.status() == AutomationCaseStatus.CREATED) {
                        casesCreated++;
                        recentTopics.add(result.generated().title());
                    } else {
                        casesFailed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to generate case {}: {}", i, e.getMessage());
                    casesFailed++;
                }
            }

            finishRun(runId, casesCreated, casesFailed);

        } catch (Exception e) {
            log.error("Run {} failed: {}", runId, e.getMessage());
            AutomationRunEntity run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomationRunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
        }
    }

    @Transactional
    public void finishRun(UUID runId, int casesCreated, int casesFailed) {
        AutomationRunEntity run = runRepository.findById(runId).orElseThrow();
        run.setCasesCreated(casesCreated);
        run.setCasesFailed(casesFailed);
        run.setFinishedAt(Instant.now());

        if (casesFailed == 0) {
            run.setStatus(AutomationRunStatus.COMPLETED);
        } else if (casesCreated > 0) {
            run.setStatus(AutomationRunStatus.PARTIAL);
        } else {
            run.setStatus(AutomationRunStatus.FAILED);
        }

        runRepository.save(run);
        log.info("Run {} finished: {} created, {} failed (status={})",
                runId, casesCreated, casesFailed, run.getStatus());
    }

    @Transactional
    public void resumeStaleRuns() {
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        List<AutomationRunEntity> activeRuns = runRepository.findActiveRunsSince(
                List.of(AutomationRunStatus.PENDING, AutomationRunStatus.RUNNING), dayStart);

        for (AutomationRunEntity run : activeRuns) {
            if (run.getStartedAt() != null &&
                Instant.now().toEpochMilli() - run.getStartedAt().toEpochMilli() > STALE_MS) {
                log.warn("Stale run {} detected, marking FAILED", run.getId());
                run.setStatus(AutomationRunStatus.FAILED);
                run.setErrorMessage("Stale recovery");
                run.setFinishedAt(Instant.now());
                runRepository.save(run);
            }
        }
    }

    private List<String> getRecentTopics() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT title FROM cases WHERE created_at > ? ORDER BY created_at DESC LIMIT 20",
                String.class,
                Instant.now().minus(java.time.Duration.ofHours(48))
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void autoEnableBots() {
        jdbcTemplate.update(
            """
            UPDATE users SET automation_enabled = true
            WHERE is_bot = true AND deleted_at IS NULL AND is_anonymous = false
            LIMIT 15
            """
        );
    }
}