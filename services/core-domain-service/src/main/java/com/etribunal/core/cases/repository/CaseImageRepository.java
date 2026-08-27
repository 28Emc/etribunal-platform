package com.etribunal.core.cases.repository;

import com.etribunal.core.cases.domain.CaseImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseImageRepository extends JpaRepository<CaseImageEntity, UUID> {

    List<CaseImageEntity> findByCaseIdOrderByOrderIndexAsc(UUID caseId);

    List<CaseImageEntity> findByCaseIdAndSideOrderByOrderIndexAsc(UUID caseId, String side);

    long countByCaseId(UUID caseId);

    void deleteByCaseIdAndSide(UUID caseId, String side);
}