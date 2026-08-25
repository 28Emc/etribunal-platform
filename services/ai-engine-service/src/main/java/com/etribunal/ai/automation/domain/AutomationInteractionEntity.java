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
@Table(name = "automation_interactions")
public class AutomationInteractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_case_id", nullable = false)
    private AutomationCaseEntity automationCase;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, columnDefinition = "varchar(20)")
    private AutomationInteractionType interactionType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "result_id")
    private String resultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private AutomationInteractionStatus status = AutomationInteractionStatus.PENDING;

    @Column(name = "stance", columnDefinition = "varchar(20)")
    private String stance;

    @Column(name = "tone", columnDefinition = "varchar(50)")
    private String tone;

    @Column(name = "plan_index")
    private Integer planIndex;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "error_code", columnDefinition = "varchar(50)")
    private String errorCode;

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
    public AutomationCaseEntity getAutomationCase() { return automationCase; }
    public void setAutomationCase(AutomationCaseEntity automationCase) { this.automationCase = automationCase; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public AutomationInteractionType getInteractionType() { return interactionType; }
    public void setInteractionType(AutomationInteractionType interactionType) { this.interactionType = interactionType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    public AutomationInteractionStatus getStatus() { return status; }
    public void setStatus(AutomationInteractionStatus status) { this.status = status; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public Integer getPlanIndex() { return planIndex; }
    public void setPlanIndex(Integer planIndex) { this.planIndex = planIndex; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Object getMetadata() { return metadata; }
    public void setMetadata(Object metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
