package com.etribunal.core.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker que drena la cola de moderación en memoria.
 * Procesa los jobs encolados (CASE, COMMENT, CASE_IMAGE) y persiste el
 * resultado (APPROVED/FLAGGED) sobre el contenido moderado.
 */
@Component
public class ModerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ModerationScheduler.class);

    private final ModerationService moderationService;

    public ModerationScheduler(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Scheduled(fixedDelayString = "${etribunal.moderation.poll-interval-ms:5000}")
    public void drainQueue() {
        int before = moderationService.queueSize();
        if (before == 0) {
            return;
        }
        moderationService.processQueuedJobs();
        log.info("Moderation queue drained: {} jobs processed", before);
    }
}