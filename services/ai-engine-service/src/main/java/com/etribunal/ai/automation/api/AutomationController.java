package com.etribunal.ai.automation.api;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.application.AutomationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final AutomationScheduler scheduler;

    public AutomationController(AutomationOrchestrator orchestrator, AutomationScheduler scheduler) {
        this.orchestrator = orchestrator;
        this.scheduler = scheduler;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> startRun() {
        log.info("Manual run triggered");
        AutomationOrchestrator.RunResult result = orchestrator.startRun(false);
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

    @GetMapping("/runs/{id}")
    public ResponseEntity<Object> getRun(@PathVariable String id) {
        return orchestrator.getRunStatus(id)
                .map(run -> ResponseEntity.ok((Object) run))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        return ResponseEntity.ok(orchestrator.getQueueStatus());
    }
}