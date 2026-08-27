package com.etribunal.core.comments;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findByCaseIdAndParentIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            UUID caseId, Pageable pageable);

    List<CommentEntity> findByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(
            UUID caseId, Instant before, Pageable pageable);

    List<CommentEntity> findByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDescIdDesc(
            UUID caseId, Instant after, Pageable pageable);

    List<CommentEntity> findByParentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(
            Collection<UUID> parentIds);

    List<CommentEntity> findByParentIdOrderByCreatedAtAsc(UUID parentId);

    long countByCaseIdAndParentIdIsNullAndDeletedAtIsNull(UUID caseId);

    long countByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtAfter(
            UUID caseId, Instant since);

    Optional<CommentEntity> findByIdAndDeletedAtIsNull(UUID id);
}
