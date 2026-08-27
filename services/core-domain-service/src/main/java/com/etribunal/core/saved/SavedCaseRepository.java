package com.etribunal.core.saved;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedCaseRepository extends JpaRepository<SavedCaseEntity, SavedCaseEntity.SavedCaseId> {

    Optional<SavedCaseEntity> findByUserIdAndCaseId(UUID userId, UUID caseId);

    List<SavedCaseEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COUNT(s) FROM SavedCaseEntity s JOIN s.caseEntity c WHERE s.userId = :userId AND c.deletedAt IS NULL")
    long countByUserIdAndCaseDeletedAtIsNull(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SavedCaseEntity s WHERE s.userId = :userId AND s.caseId = :caseId")
    int deleteByUserIdAndCaseId(@Param("userId") UUID userId, @Param("caseId") UUID caseId);

    @Query("SELECT s.caseId FROM SavedCaseEntity s WHERE s.userId = :userId AND s.caseId IN :caseIds")
    List<UUID> findCaseIdsByUserIdAndCaseIdIn(@Param("userId") UUID userId,
                                              @Param("caseIds") List<UUID> caseIds);
}