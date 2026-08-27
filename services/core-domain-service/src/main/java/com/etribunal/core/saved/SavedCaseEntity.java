package com.etribunal.core.saved;

import com.etribunal.core.cases.CaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_cases")
@IdClass(SavedCaseEntity.SavedCaseId.class)
public class SavedCaseEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "case_id", insertable = false, updatable = false)
    private CaseEntity caseEntity;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public CaseEntity getCaseEntity() {
        return caseEntity;
    }

    public void setCaseEntity(CaseEntity caseEntity) {
        this.caseEntity = caseEntity;
    }

    @Embeddable
    public static class SavedCaseId implements java.io.Serializable {
        private UUID userId;
        private UUID caseId;

        public SavedCaseId() {
        }

        public SavedCaseId(UUID userId, UUID caseId) {
            this.userId = userId;
            this.caseId = caseId;
        }

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public UUID getCaseId() {
            return caseId;
        }

        public void setCaseId(UUID caseId) {
            this.caseId = caseId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SavedCaseId that = (SavedCaseId) o;
            return userId.equals(that.userId) && caseId.equals(that.caseId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, caseId);
        }
    }
}