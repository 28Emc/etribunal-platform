package com.etribunal.core.reports;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.ModerationStatus;
import com.etribunal.core.notifications.NotificationService;
import com.etribunal.core.notifications.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    private final CaseReportRepository caseReportRepository;
    private final CaseRepository caseRepository;
    private final NotificationService notificationService;

    public ReportService(CaseReportRepository caseReportRepository,
                         CaseRepository caseRepository,
                         NotificationService notificationService) {
        this.caseReportRepository = caseReportRepository;
        this.caseRepository = caseRepository;
        this.notificationService = notificationService;
    }

    /**
     * Report a case (moderator only).
     * Creates CaseReport, updates case status to REPORTED/FLAGGED, notifies creator.
     */
    @Transactional
    public ReportActionResponse reportCase(UUID caseId, UUID moderatorId, String reason) {
        CaseEntity caseEntity = requireCase(caseId);

        if (caseEntity.getReportStatus() == ReportStatus.REPORTED
                && caseEntity.getModerationStatus() == com.etribunal.core.cases.ModerationStatus.FLAGGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este caso ya está en revisión. Espera a que el usuario realice cambios.");
        }

        // Create report record
        CaseReportEntity report = new CaseReportEntity();
        report.setId(UUID.randomUUID());
        report.setCaseId(caseId);
        report.setReporterId(moderatorId);
        report.setReason(reason);
        report.setCreatedAt(Instant.now());
        caseReportRepository.save(report);

        // Update case status
        caseEntity.setReportStatus(ReportStatus.REPORTED);
        caseEntity.setModerationStatus(com.etribunal.core.cases.ModerationStatus.FLAGGED);
        caseEntity.setReportReason(reason);
        caseEntity.setReportedById(moderatorId);
        caseRepository.save(caseEntity);

        // Notify case creator (Side A)
        notificationService.createNotification(caseEntity.getSideAUserId(), moderatorId,
                NotificationType.CASE_REPORTED,
                Map.of("case_id", caseId.toString(),
                       "case_title", caseEntity.getTitle(),
                       "report_reason", reason));

        return new ReportActionResponse(true, "Caso reportado exitosamente");
    }

    /**
     * Get reports for a case (creator only).
     */
    @Transactional(readOnly = true)
    public CaseReportsResponse getCaseReports(UUID caseId, UUID userId) {
        CaseEntity caseEntity = requireCase(caseId);

        if (!caseEntity.getSideAUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el creador del caso puede ver los reportes");
        }

        List<CaseReportEntity> reports = caseReportRepository.findByCaseIdOrderByCreatedAtDesc(caseId);

        List<ReportResponse> responses = reports.stream()
                .map(r -> new ReportResponse(
                        r.getId().toString(),
                        r.getReason(),
                        r.getCreatedAt(),
                        r.getReporterId().toString()))
                .toList();

        return new CaseReportsResponse(responses);
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));
    }

    // --- Response Records (snake_case) ---

    public record ReportResponse(String id, String reason, Instant created_at, String reporter_id) {
    }

    public record CaseReportsResponse(List<ReportResponse> reports) {
    }
    
    public record ReportActionResponse(boolean success, String message) {
    }
}