package com.etribunal.core.analytics;

import com.etribunal.core.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SysadminApiKeyGuard sysadminGuard;

    public AnalyticsController(AnalyticsService analyticsService,
                               SysadminApiKeyGuard sysadminGuard) {
        this.analyticsService = analyticsService;
        this.sysadminGuard = sysadminGuard;
    }

    @GetMapping("/analytics/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> globalKPIs(
            HttpServletRequest request) {
        sysadminGuard.assertAuthorized(request.getHeader("X-Sysadmin-Api-Key"));
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getGlobalKPIs()));
    }

    @GetMapping("/analytics/cases/{caseId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> caseKPIs(
            @PathVariable UUID caseId, HttpServletRequest request) {
        sysadminGuard.assertAuthorized(request.getHeader("X-Sysadmin-Api-Key"));
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCaseKPIs(caseId)));
    }
}