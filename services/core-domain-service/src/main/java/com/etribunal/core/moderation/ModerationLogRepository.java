package com.etribunal.core.moderation;

import com.etribunal.core.cases.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ModerationLogRepository extends JpaRepository<ModerationLogEntity, UUID> {

    List<ModerationLogEntity> findByTargetTypeAndTargetId(String targetType, UUID targetId);

    @Query("SELECT m FROM ModerationLogEntity m WHERE m.moderationStatus = :status ORDER BY m.createdAt DESC")
    List<ModerationLogEntity> findByModerationStatus(@Param("status") ModerationStatus status);
}