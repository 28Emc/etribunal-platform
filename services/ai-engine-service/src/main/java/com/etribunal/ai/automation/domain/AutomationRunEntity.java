package com.etribunal.ai.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_runs")
public class AutomationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private AutomationRunStatus status = AutomationRunStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cases_requested", nullable = false)
    private int casesRequested;

    @Column(name = "cases_created", nullable = false)
    private int casesCreated;

    @Column(name = "cases_failed", nullable = false)
    private int casesFailed;

    @Column(name = "interactions_per_case", nullable = false)
    private int interactionsPerCase;

    @Column(name = "interaction_intensity", nullable = false)
    private int interactionIntensity;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Object metadata;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

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
    public AutomationRunStatus getStatus() { return status; }
    public void setStatus(AutomationRunStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public int getCasesRequested() { return casesRequested; }
    public void setCasesRequested(int casesRequested) { this.casesRequested = casesRequested; }
    public int getCasesCreated() { return casesCreated; }
    public void setCasesCreated(int casesCreated) { this.casesCreated = casesCreated; }
    public int getCasesFailed() { return casesFailed; }
    public void setCasesFailed(int casesFailed) { this.casesFailed = casesFailed; }
    public int getInteractionsPerCase() { return interactionsPerCase; }
    public void setInteractionsPerCase(int interactionsPerCase) { this.interactionsPerCase = interactionsPerCase; }
    public int getInteractionIntensity() { return interactionIntensity; }
    public void setInteractionIntensity(int interactionIntensity) { this.interactionIntensity = interactionIntensity; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public Object getMetadata() { return metadata; }
    public void setMetadata(Object metadata) { this.metadata = metadata; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
