package com.etribunal.ai.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_cases")
public class AutomationCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_run_id", nullable = false)
    private AutomationRunEntity run;

    @Column(name = "case_id", nullable = false, unique = true)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private AutomationCaseStatus status = AutomationCaseStatus.PLANNED;

    @Column(name = "target_interactions", nullable = false)
    private int targetInteractions;

    @Column(name = "successful_interactions", nullable = false)
    private int successfulInteractions;

    @Column(name = "failed_interactions", nullable = false)
    private int failedInteractions;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_result", columnDefinition = "varchar(20)")
    private com.etribunal.ai.moderation.domain.ModerationStatus moderationResult;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Object metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AutomationRunEntity getRun() { return run; }
    public void setRun(AutomationRunEntity run) { this.run = run; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public AutomationCaseStatus getStatus() { return status; }
    public void setStatus(AutomationCaseStatus status) { this.status = status; }
    public int getTargetInteractions() { return targetInteractions; }
    public void setTargetInteractions(int targetInteractions) { this.targetInteractions = targetInteractions; }
    public int getSuccessfulInteractions() { return successfulInteractions; }
    public void setSuccessfulInteractions(int successfulInteractions) { this.successfulInteractions = successfulInteractions; }
    public int getFailedInteractions() { return failedInteractions; }
    public void setFailedInteractions(int failedInteractions) { this.failedInteractions = failedInteractions; }
    public com.etribunal.ai.moderation.domain.ModerationStatus getModerationResult() { return moderationResult; }
    public void setModerationResult(com.etribunal.ai.moderation.domain.ModerationStatus moderationResult) { this.moderationResult = moderationResult; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Object getMetadata() { return metadata; }
    public void setMetadata(Object metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
