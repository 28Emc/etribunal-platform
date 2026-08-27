package com.etribunal.core.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CaseReportRepository extends JpaRepository<CaseReportEntity, UUID> {

    List<CaseReportEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    @Query("SELECT COUNT(r) FROM CaseReportEntity r WHERE r.caseId = :caseId")
    long countByCaseId(@Param("caseId") UUID caseId);
}