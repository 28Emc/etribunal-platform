package com.etribunal.core.moderation;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.ModerationStatus;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import com.etribunal.core.comments.CommentEntity;
import com.etribunal.core.comments.CommentRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final ModerationProvider provider;
    private final ModerationQueue queue;
    private final ModerationLogRepository logRepository;
    private final CaseRepository caseRepository;
    private final CommentRepository commentRepository;
    private final CaseImageRepository caseImageRepository;

    public ModerationService(
            ModerationProvider provider,
            ModerationQueue queue,
            ModerationLogRepository logRepository,
            CaseRepository caseRepository,
            CommentRepository commentRepository,
            CaseImageRepository caseImageRepository) {
        this.provider = provider;
        this.queue = queue;
        this.logRepository = logRepository;
        this.caseRepository = caseRepository;
        this.commentRepository = commentRepository;
        this.caseImageRepository = caseImageRepository;
    }

    /**
     * Moderación síncrona para casos críticos (creación de caso, edición de contenido)
     */
    public Mono<ModerationResult> moderateCaseContentSync(UUID caseId, String title, String sideAContent, String sideBContent) {
        String combined = (title != null ? title : "") + " " +
                (sideAContent != null ? sideAContent : "") + " " +
                (sideBContent != null ? sideBContent : "");

        return provider.moderateText(combined)
                .doOnNext(result -> persistLogAndUpdateCase(caseId, "CASE", combined, result, "LOCAL"));
    }

    /**
     * Moderación síncrona para comentarios
     */
    public Mono<ModerationResult> moderateCommentSync(UUID commentId, String content) {
        return provider.moderateText(content)
                .doOnNext(result -> persistLogAndUpdateComment(commentId, content, result, "LOCAL"));
    }

    /**
     * Moderación síncrona para imágenes de casos
     */
    public Mono<ModerationResult> moderateCaseImageSync(UUID imageId, String imageUrl) {
        return provider.moderateImage(imageUrl)
                .doOnNext(result -> persistLogAndUpdateCaseImage(imageId, imageUrl, result, "LOCAL"));
    }

    /**
     * Moderación asíncrona para contenido menos crítico
     */
    public void moderateCaseContentAsync(UUID caseId, String title, String sideAContent, String sideBContent) {
        String combined = (title != null ? title : "") + " " +
                (sideAContent != null ? sideAContent : "") + " " +
                (sideBContent != null ? sideBContent : "");
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("CASE", caseId, combined);
        queue.enqueue(job);
    }

    public void moderateCommentAsync(UUID commentId, String content) {
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("COMMENT", commentId, content);
        queue.enqueue(job);
    }

    public void moderateCaseImageAsync(UUID imageId, String imageUrl) {
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("CASE_IMAGE", imageId, imageUrl);
        queue.enqueue(job);
    }

    public int queueSize() {
        return queue.size();
    }

    @Transactional
    public void processQueuedJobs() {
        ModerationQueue.ModerationJob job;
        while ((job = queue.poll()) != null) {
            final ModerationQueue.ModerationJob currentJob = job;
            try {
                switch (currentJob.targetType()) {
                    case "CASE" -> {
                        CaseEntity caseEntity = caseRepository.findById(currentJob.targetId()).orElse(null);
                        if (caseEntity != null) {
                            provider.moderateText(currentJob.contentText())
                                    .doOnNext(result -> persistLogAndUpdateCase(currentJob.targetId(), "CASE", currentJob.contentText(), result, "LOCAL"))
                                    .subscribe();
                        }
                    }
                    case "COMMENT" -> {
                        CommentEntity comment = commentRepository.findById(currentJob.targetId()).orElse(null);
                        if (comment != null) {
                            provider.moderateText(currentJob.contentText())
                                    .doOnNext(result -> persistLogAndUpdateComment(currentJob.targetId(), currentJob.contentText(), result, "LOCAL"))
                                    .subscribe();
                        }
                    }
                    case "CASE_IMAGE" -> {
                        CaseImageEntity image = caseImageRepository.findById(currentJob.targetId()).orElse(null);
                        if (image != null) {
                            provider.moderateImage(currentJob.contentText())
                                    .doOnNext(result -> persistLogAndUpdateCaseImage(currentJob.targetId(), currentJob.contentText(), result, "LOCAL"))
                                    .subscribe();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing moderation job {}: {}", currentJob.id(), e.getMessage());
            }
        }
    }

    private void persistLogAndUpdateCase(UUID caseId, String targetType, String content, ModerationResult result, String providerName) {
        // Log
        ModerationLogEntity logEntity = new ModerationLogEntity();
        logEntity.setTargetType(targetType);
        logEntity.setTargetId(caseId);
        logEntity.setContentText(content);
        logEntity.setModerationStatus(result.status());
        logEntity.setRiskScore(result.riskScore());
        logEntity.setProvider(providerName);
        logEntity.setMatchedRules(result.matchedRules());
        logRepository.save(logEntity);

        // Update case
        CaseEntity caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity != null) {
            caseEntity.setModerationStatus(result.status());
            caseEntity.setRiskScore(result.riskScore());
            if (result.status() == ModerationStatus.FLAGGED) {
                caseEntity.setReportStatus(com.etribunal.core.reports.ReportStatus.REPORTED);
            }
            caseRepository.save(caseEntity);
            log.info("Case {} moderated: status={}, riskScore={}", caseId, result.status(), result.riskScore());
        }
    }

    private void persistLogAndUpdateComment(UUID commentId, String content, ModerationResult result, String providerName) {
        ModerationLogEntity logEntity = new ModerationLogEntity();
        logEntity.setTargetType("COMMENT");
        logEntity.setTargetId(commentId);
        logEntity.setContentText(content);
        logEntity.setModerationStatus(result.status());
        logEntity.setRiskScore(result.riskScore());
        logEntity.setProvider(providerName);
        logEntity.setMatchedRules(result.matchedRules());
        logRepository.save(logEntity);

        CommentEntity comment = commentRepository.findById(commentId).orElse(null);
        if (comment != null) {
            comment.setModerationStatus(result.status());
            comment.setRiskScore(result.riskScore());
            commentRepository.save(comment);
            log.info("Comment {} moderated: status={}, riskScore={}", commentId, result.status(), result.riskScore());
        }
    }

    private void persistLogAndUpdateCaseImage(UUID imageId, String imageUrl, ModerationResult result, String providerName) {
        ModerationLogEntity logEntity = new ModerationLogEntity();
        logEntity.setTargetType("CASE_IMAGE");
        logEntity.setTargetId(imageId);
        logEntity.setContentText(imageUrl);
        logEntity.setModerationStatus(result.status());
        logEntity.setRiskScore(result.riskScore());
        logEntity.setProvider(providerName);
        logEntity.setMatchedRules(result.matchedRules());
        logRepository.save(logEntity);

        CaseImageEntity image = caseImageRepository.findById(imageId).orElse(null);
        if (image != null) {
            image.setModerationStatus(result.status());
            caseImageRepository.save(image);
            log.info("CaseImage {} moderated: status={}, riskScore={}", imageId, result.status(), result.riskScore());
        }
    }

    public List<ModerationLogEntity> getModerationHistory(String targetType, UUID targetId) {
        return logRepository.findByTargetTypeAndTargetId(targetType, targetId);
    }

    public List<ModerationLogEntity> getFlaggedContent() {
        return logRepository.findByModerationStatus(ModerationStatus.FLAGGED);
    }
}