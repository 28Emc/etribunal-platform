package com.etribunal.core.moderation;

import com.etribunal.core.cases.ModerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Component
public class ModerationQueue {

    private static final Logger log = LoggerFactory.getLogger(ModerationQueue.class);

    private final ConcurrentLinkedQueue<ModerationJob> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, ModerationResult> results = new ConcurrentHashMap<>();

    public void enqueue(ModerationJob job) {
        queue.offer(job);
        log.debug("Enqueued moderation job: {} for {}:{}", job.id(), job.targetType(), job.targetId());
    }

    public ModerationJob poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public void storeResult(String jobId, ModerationResult result) {
        results.put(jobId, result);
    }

    public Mono<ModerationResult> getResultAsync(String jobId) {
        return Mono.fromCallable(() -> {
            ModerationResult result = results.get(jobId);
            if (result == null) {
                throw new IllegalStateException("Result not ready for job: " + jobId);
            }
            return result;
        });
    }

    public void processNext(ModerationProvider provider, Consumer<ModerationJob> onProcessed) {
        ModerationJob job = queue.poll();
        if (job == null) return;

        provider.moderateText(job.contentText())
                .doOnNext(result -> {
                    storeResult(job.id(), result);
                    onProcessed.accept(job);
                })
                .doOnError(e -> {
                    log.error("Moderation failed for job {}: {}", job.id(), e.getMessage());
                    ModerationResult errorResult = new ModerationResult(
                            ModerationStatus.APPROVED, 0.0, List.of("error:" + e.getMessage()), Map.of());
                    storeResult(job.id(), errorResult);
                    onProcessed.accept(job);
                })
                .subscribe();
    }

    public record ModerationJob(
            String id,
            String targetType,
            UUID targetId,
            String contentText
    ) {
        public ModerationJob(String targetType, UUID targetId, String contentText) {
            this(UUID.randomUUID().toString(), targetType, targetId, contentText);
        }
    }
}