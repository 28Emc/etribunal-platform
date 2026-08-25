package com.etribunal.core.reports;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportsController {

    private final ReportService reportService;
    private final CurrentUserResolver currentUser;

    public ReportsController(ReportService reportService,
                             CurrentUserResolver currentUser) {
        this.reportService = reportService;
        this.currentUser = currentUser;
    }

    public record ReportCaseRequest(
            @NotBlank String reason
    ) {
    }

    // POST /cases/{id}/report
    @PostMapping("/cases/{id}/report")
    public ResponseEntity<ApiResponse<ReportService.ReportActionResponse>> reportCase(
            @PathVariable UUID id,
            @RequestBody ReportCaseRequest dto,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                reportService.reportCase(id, userId, dto.reason())));
    }

    // GET /cases/{id}/reports
    @GetMapping("/cases/{id}/reports")
    public ResponseEntity<ApiResponse<ReportService.CaseReportsResponse>> getCaseReports(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                reportService.getCaseReports(id, userId)));
    }
}