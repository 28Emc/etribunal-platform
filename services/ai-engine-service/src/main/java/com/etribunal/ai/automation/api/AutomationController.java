package com.etribunal.ai.automation.api;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.settings.AutomationSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Map;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private static final Logger log = LoggerFactory.getLogger(AutomationController.class);

    private final AutomationOrchestrator orchestrator;
    private final AutomationSettingsService settingsService;
    private final EngagementService engagementService;
    private final AutomationConfig config;
    private final AutomationAdminGuard adminGuard;
    private final AutomationWebSocketController wsController;

    public AutomationController(
            AutomationOrchestrator orchestrator,
            AutomationSettingsService settingsService,
            EngagementService engagementService,
            AutomationConfig config,
            AutomationAdminGuard adminGuard,
            AutomationWebSocketController wsController
    ) {
        this.orchestrator = orchestrator;
        this.settingsService = settingsService;
        this.engagementService = engagementService;
        this.config = config;
        this.adminGuard = adminGuard;
        this.wsController = wsController;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> startRun(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        adminGuard.assertAdmin(authorization);
        log.info("Manual run triggered (dryRun={})", dryRun);
        AutomationOrchestrator.RunResult result = orchestrator.startRun(dryRun);
        return ResponseEntity.accepted().body(Map.of(
                "runId", result.runId().toString(),
                "started", result.started(),
                "status", result.status(),
                "pollingUrl", result.pollingUrl()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "ai-engine",
                "uptime", runtime.getUptime()
        ));
    }

    @GetMapping("/runs")
    public ResponseEntity<Object> getRuns(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "20") int limit) {
        adminGuard.assertAdmin(authorization);
        return ResponseEntity.ok(orchestrator.getRecentRuns(limit));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<Object> getRun(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String id) {
        adminGuard.assertAdmin(authorization);
        return orchestrator.getRunStatus(id)
                .map(run -> ResponseEntity.ok((Object) run))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        adminGuard.assertAdmin(authorization);
        return ResponseEntity.ok(orchestrator.getQueueStatus());
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        adminGuard.assertAdmin(authorization);
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody Map<String, Object> changes) {
        adminGuard.assertAdmin(authorization);
        log.info("Updating automation settings with {} keys", changes == null ? 0 : changes.size());
        Map<String, Object> updated = settingsService.updateSettings(changes);
        wsController.broadcastSettingsUpdate(updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/engagement")
    public ResponseEntity<Map<String, Object>> getEngagement(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        adminGuard.assertAdmin(authorization);
        return ResponseEntity.ok(engagementService.getAnalyticsSummary(
                Math.max(1, config.getEngagement().getTopExamples())));
    }
}

