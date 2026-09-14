package com.etribunal.core.comments;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    /**
     * Conteo total de comentarios (raíz + respuestas, sin borrados) agrupado por
     * caso. Fuente autoritativa para {@code total_comments}, igual que el legacy
     * counter que incrementa 1 por comentario creado y resta 1+replies al borrar.
     */
    @Query("""
            SELECT c.caseId AS caseId, COUNT(c) AS total
            FROM CommentEntity c
            WHERE c.caseId IN :caseIds AND c.deletedAt IS NULL
            GROUP BY c.caseId
            """)
    List<CaseCommentCount> countByCaseIdIn(@Param("caseIds") Collection<UUID> caseIds);

    interface CaseCommentCount {
        UUID getCaseId();

        long getTotal();
    }

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
