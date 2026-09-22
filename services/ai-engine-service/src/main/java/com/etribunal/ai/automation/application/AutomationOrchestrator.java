package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.api.AutomationWebSocketController;
import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AutomationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AutomationOrchestrator.class);
    private static final long STALE_MS = 30L * 60 * 1000;

    private static final String K_STATUS = "status";
    private static final String K_DRY_RUN = "dryRun";
    private static final String K_CASES_REQUESTED = "casesRequested";
    private static final String K_CASES_CREATED = "casesCreated";
    private static final String K_CASES_FAILED = "casesFailed";
    private static final String K_STARTED_AT = "startedAt";
    private static final String K_FINISHED_AT = "finishedAt";
    private static final String K_ERROR_MESSAGE = "errorMessage";
    private static final String STALE_RECOVERY_MESSAGE = "Stale recovery";

    private final AutomationConfig config;
    private final AutomationRunRepository runRepository;
    private final AutomationCaseRepository caseRepository;
    private final AutomationInteractionRepository interactionRepository;
    private final CaseGenerator caseGenerator;
    private final InteractionPlanner interactionPlanner;
    private final InteractionExecutor interactionExecutor;
    private final UserSelector userSelector;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate identityJdbcTemplate;
    private final AutomationOrchestrator self;
    private final AutomationWebSocketController wsController;

    public AutomationOrchestrator(
            AutomationConfig config,
            AutomationRunRepository runRepository,
            AutomationCaseRepository caseRepository,
            AutomationInteractionRepository interactionRepository,
            CaseGenerator caseGenerator,
            InteractionPlanner interactionPlanner,
            InteractionExecutor interactionExecutor,
            UserSelector userSelector,
            JdbcTemplate jdbcTemplate,
            @Qualifier("identityJdbcTemplate") JdbcTemplate identityJdbcTemplate,
            @Lazy AutomationOrchestrator self,
            @Lazy AutomationWebSocketController wsController
    ) {
        this.config = config;
        this.runRepository = runRepository;
        this.caseRepository = caseRepository;
        this.interactionRepository = interactionRepository;
        this.caseGenerator = caseGenerator;
        this.interactionPlanner = interactionPlanner;
        this.interactionExecutor = interactionExecutor;
        this.userSelector = userSelector;
        this.jdbcTemplate = jdbcTemplate;
        this.identityJdbcTemplate = identityJdbcTemplate;
        this.self = self;
        this.wsController = wsController;
    }

    public record RunResult(UUID runId, boolean started, String status, String pollingUrl) {}

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            Object key = entries[i];
            Object value = entries[i + 1];
            if (value != null) {
                map.put(key.toString(), value);
            }
        }
        return map;
    }

    public RunResult startRun(boolean dryRunOverride) {
        if (!config.isEnabled()) {
            log.info("Automation disabled (AI_ENABLED=false), refusing to start run");
            throw new IllegalStateException("Automation is disabled (AI_ENABLED=false)");
        }
        return doStartRun(dryRunOverride);
    }

    /**
     * Sincronizado: el check-then-act (¿hay run activo? → crear) debe ser atómico frente a
     * las tres entradas (REST, WebSocket y cron). Sin esto dos llamadas concurrentes podrían
     * crear dos runs solapados del día.
     */
    private synchronized RunResult doStartRun(boolean dryRunOverride) {

        Instant todayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        Optional<AutomationRunEntity> activeRun = runRepository.findFirstByStatusInAndCreatedAtAfter(
                List.of(AutomationRunStatus.PENDING, AutomationRunStatus.RUNNING), todayStart);

        if (activeRun.isPresent()) {
            AutomationRunEntity existing = activeRun.get();
            if (existing.getStartedAt() != null &&
                Instant.now().toEpochMilli() - existing.getStartedAt().toEpochMilli() > STALE_MS) {
                existing.setStatus(AutomationRunStatus.FAILED);
                existing.setErrorMessage(STALE_RECOVERY_MESSAGE);
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
                        K_DRY_RUN, dryRun,
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
        
        // Broadcast initial run state
        wsController.broadcastRunUpdate(mapOf(
            "id", runId.toString(),
            K_STATUS, "PENDING",
            K_DRY_RUN, dryRun,
            K_CASES_REQUESTED, dailyCases,
            K_CASES_CREATED, 0,
            K_CASES_FAILED, 0
        ));

        self.launchRun(runId, dryRun, dailyCases, usersPerCase, intensity, maxPerUser, schedulingInterval);

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

            // Broadcast RUNNING status
            wsController.broadcastRunUpdate(mapOf(
                "id", runId.toString(),
                K_STATUS, "RUNNING",
                K_STARTED_AT, run.getStartedAt().toString()
            ));

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

            GenerationSummary summary = generateCasesForRun(
                    new GenerationContext(runId, dailyCases, recentTopics, pool, dryRun, usersPerCase, intensity, maxPerUser));

            log.info("Run {} scheduled {} interactions across {} created cases",
                    runId, summary.interactionsScheduled(), summary.casesCreated());

            self.finishRun(runId, summary.casesCreated(), summary.casesFailed());

            if (summary.interactionsScheduled() > 0) {
                broadcastQueueStatus();
            }

        } catch (Exception e) {
            log.error("Run {} failed: {}", runId, e.getMessage());
            AutomationRunEntity run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomationRunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(Instant.now());
            runRepository.save(run);

            // Broadcast FAILED status
            wsController.broadcastRunUpdate(mapOf(
                "id", runId.toString(),
                K_STATUS, "FAILED",
                K_ERROR_MESSAGE, e.getMessage(),
                K_FINISHED_AT, Instant.now().toString()
            ));
        }
    }

    private GenerationSummary generateCasesForRun(GenerationContext ctx) {
        int casesCreated = 0;
        int casesFailed = 0;
        int interactionsScheduled = 0;

        UUID runId = ctx.runId();
        List<String> recentTopics = ctx.recentTopics();
        List<UserSelector.BotUser> pool = ctx.pool();
        boolean dryRun = ctx.dryRun();

        for (int i = 0; i < ctx.dailyCases(); i++) {
            try {
                CaseGenerator.CaseResult result = caseGenerator.generateCase(
                        runId, i, recentTopics, pool, dryRun).block();

                if (result != null
                        && (result.status() == AutomationCaseStatus.CREATED
                            || (dryRun && result.status() == AutomationCaseStatus.PLANNED))) {
                    casesCreated++;
                    recentTopics.add(result.generated().title());
                    if (!dryRun && result.caseId() != null) {
                        interactionsScheduled += planAndScheduleInteractions(
                                runId, result, pool, ctx.usersPerCase(), ctx.intensity(), ctx.maxPerUser());
                    }
                } else {
                    casesFailed++;
                }
            } catch (Exception e) {
                log.error("Failed to generate case {}: {}", i, e.getMessage());
                casesFailed++;
            }
        }

        return new GenerationSummary(casesCreated, casesFailed, interactionsScheduled);
    }

    private record GenerationContext(
            UUID runId,
            int dailyCases,
            List<String> recentTopics,
            List<UserSelector.BotUser> pool,
            boolean dryRun,
            int usersPerCase,
            int intensity,
            int maxPerUser
    ) {}

    private record GenerationSummary(int casesCreated, int casesFailed, int interactionsScheduled) {}

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

        // Broadcast final run state
        wsController.broadcastRunUpdate(mapOf(
            "id", runId.toString(),
            K_STATUS, run.getStatus().name(),
            K_CASES_CREATED, casesCreated,
            K_CASES_FAILED, casesFailed,
            K_FINISHED_AT, run.getFinishedAt().toString()
        ));

        // Refrescar KPIs de cola en el panel admin
        wsController.broadcastQueueUpdate(getQueueStatus());
    }

    /**
     * Genera el plan de interacciones para un caso recién creado (vía IA) y lo agenda
     * en la cola del scheduler. Si el pool no tiene usuarios elegibles o el plan es
     * inválido, se registra y continúa sin romper el run.
     */
    private int planAndScheduleInteractions(
            UUID runId,
            CaseGenerator.CaseResult result,
            List<UserSelector.BotUser> pool,
            int interactionCount,
            int intensity,
            int maxPerUser
    ) {
        try {
            com.etribunal.ai.automation.domain.dtos.GeneratedCase generated = result.generated();
            if (generated == null || result.caseId() == null) {
                log.warn("Run {} skips interactions: case {} has no generated payload", runId, result.caseId());
                return 0;
            }

            Optional<AutomationCaseEntity> automationCaseOpt = caseRepository.findByCaseId(result.caseId());
            if (automationCaseOpt.isEmpty()) {
                log.warn("Run {} skips interactions: automation case not found for {}", runId, result.caseId());
                return 0;
            }
            AutomationCaseEntity automationCase = automationCaseOpt.get();

            InteractionPlanner.PlanResult plan = interactionPlanner.generate(
                    new InteractionPlanner.PlanInput(
                            result.caseId(),
                            generated.title(),
                            generated.sideAContent(),
                            generated.sideBContent(),
                            generated.category(),
                            interactionCount,
                            intensity,
                            pool,
                            result.authorId(),
                            result.sideBUserId(),
                            maxPerUser
                    )
            ).block();

            if (plan == null || plan.interactions().isEmpty()) {
                log.warn("Run {} got empty interaction plan for case {}", runId, result.caseId());
                return 0;
            }

            automationCase.setTargetInteractions(plan.interactions().size());
            caseRepository.save(automationCase);

            List<AutomationInteractionEntity> scheduled = interactionExecutor.scheduleInteractions(
                    automationCase.getId(),
                    plan.interactions(),
                    Instant.now(),
                    config.getSchedulingIntervalMin(),
                    config.getSchedulingIntervalMax(),
                    config.getSchedulingWindowHours()
            );

            log.info("Run {} scheduled {} interactions for case {}",
                    runId, scheduled.size(), result.caseId());
            return scheduled.size();
        } catch (Exception e) {
            log.error("Failed to plan/schedule interactions for case {}: {}", result.caseId(), e.getMessage());
            return 0;
        }
    }

    public void broadcastQueueStatus() {
        wsController.broadcastQueueUpdate(getQueueStatus());
    }

    public void broadcastEngagement(Map<String, Object> summary) {
        wsController.broadcastEngagementUpdate(summary);
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
                run.setErrorMessage(STALE_RECOVERY_MESSAGE);
                run.setFinishedAt(Instant.now());
                runRepository.save(run);

                // Broadcast FAILED status for stale run
                wsController.broadcastRunUpdate(mapOf(
                    "id", run.getId().toString(),
                    K_STATUS, "FAILED",
                    K_ERROR_MESSAGE, STALE_RECOVERY_MESSAGE,
                    K_FINISHED_AT, Instant.now().toString()
                ));
            }
        }
    }

    @Transactional
    public Optional<Map<String, Object>> getRunStatus(String runId) {
        try {
            UUID id = UUID.fromString(runId);
            return runRepository.findById(id).map(run -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", run.getId().toString());
                result.put(K_STATUS, run.getStatus().name());
                result.put(K_DRY_RUN, run.isDryRun());
                result.put(K_CASES_REQUESTED, run.getCasesRequested());
                result.put(K_CASES_CREATED, run.getCasesCreated());
                result.put(K_CASES_FAILED, run.getCasesFailed());
                result.put(K_STARTED_AT, run.getStartedAt() != null ? run.getStartedAt().toString() : null);
                result.put(K_FINISHED_AT, run.getFinishedAt() != null ? run.getFinishedAt().toString() : null);
                result.put(K_ERROR_MESSAGE, run.getErrorMessage());
                return result;
            });
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentRuns(int limit) {
        List<AutomationRunEntity> runs = runRepository.findRecentRuns();
        int effectiveLimit = Math.clamp(limit, 1, 100);
        return runs.stream()
                .limit(effectiveLimit)
                .map(run -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", run.getId().toString());
                    result.put(K_STATUS, run.getStatus().name());
                    result.put(K_DRY_RUN, run.isDryRun());
                    result.put(K_CASES_REQUESTED, run.getCasesRequested());
                    result.put(K_CASES_CREATED, run.getCasesCreated());
                    result.put(K_CASES_FAILED, run.getCasesFailed());
                    result.put(K_STARTED_AT, run.getStartedAt() != null ? run.getStartedAt().toString() : null);
                    result.put(K_FINISHED_AT, run.getFinishedAt() != null ? run.getFinishedAt().toString() : null);
                    result.put(K_ERROR_MESSAGE, run.getErrorMessage());
                    return result;
                })
                .toList();
    }

    public Map<String, Object> getQueueStatus() {
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        long scheduled = interactionRepository.countByStatus(AutomationInteractionStatus.SCHEDULED);
        long processing = interactionRepository.countByStatus(AutomationInteractionStatus.PROCESSING);
        long completed = interactionRepository.countByStatusAndExecutedAtGreaterThanEqual(
                AutomationInteractionStatus.SUCCESS, dayStart);
        long failed = interactionRepository.countByStatusAndExecutedAtGreaterThanEqual(
                AutomationInteractionStatus.FAILED, dayStart);

        return Map.of(
                "scheduled", scheduled,
                "processing", processing,
                "completedToday", completed,
                "failedToday", failed
        );
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
        identityJdbcTemplate.update(
            """
            UPDATE users SET automation_enabled = true
            WHERE is_bot = true AND deleted_at IS NULL AND is_anonymous = false
            LIMIT 15
            """
        );
    }
}