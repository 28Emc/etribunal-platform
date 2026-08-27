package com.etribunal.core.saved;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseShareRepository extends JpaRepository<CaseShareEntity, CaseShareEntity.CaseShareId> {

    Optional<CaseShareEntity> findByUserIdAndCaseId(UUID userId, UUID caseId);

    List<CaseShareEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COUNT(s) FROM CaseShareEntity s JOIN s.caseEntity c WHERE s.userId = :userId AND c.deletedAt IS NULL")
    long countByUserIdAndCaseDeletedAtIsNull(@Param("userId") UUID userId);

    @Query("SELECT COUNT(s) FROM CaseShareEntity s WHERE s.caseId = :caseId")
    long countByCaseId(@Param("caseId") UUID caseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CaseShareEntity s WHERE s.userId = :userId AND s.caseId = :caseId")
    int deleteByUserIdAndCaseId(@Param("userId") UUID userId, @Param("caseId") UUID caseId);
}