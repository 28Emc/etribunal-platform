package com.etribunal.ai.automation.infrastructure.ai;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

public class RateLimiter {

    private final int rpm;
    private final int rpd;
    private final int tpm;
    private final AtomicLong minuteWindowStart = new AtomicLong(Instant.now().toEpochMilli());
    private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
    private final AtomicLong dayWindowStart = new AtomicLong(Instant.now().toEpochMilli());
    private final AtomicInteger requestsToday = new AtomicInteger(0);
    private final AtomicInteger tokensThisMinute = new AtomicInteger(0);
    private final AtomicLong tokenWindowStart = new AtomicLong(Instant.now().toEpochMilli());

    public RateLimiter(int rpm, int rpd, int tpm) {
        this.rpm = rpm;
        this.rpd = rpd;
        this.tpm = tpm;
    }

    public Mono<Void> acquire(int estimatedTokens) {
        return Mono.defer(() -> {
            long now = Instant.now().toEpochMilli();

            // Reset minute window if needed
            long minuteStart = minuteWindowStart.get();
            if (now - minuteStart >= 60_000) {
                if (minuteWindowStart.compareAndSet(minuteStart, now)) {
                    requestsThisMinute.set(0);
                    tokensThisMinute.set(0);
                    tokenWindowStart.set(now);
                }
            }

            // Reset day window if needed
            long dayStart = dayWindowStart.get();
            if (now - dayStart >= 86_400_000) {
                if (dayWindowStart.compareAndSet(dayStart, now)) {
                    requestsToday.set(0);
                }
            }

            int currentRpm = requestsThisMinute.incrementAndGet();
            if (currentRpm > rpm) {
                return Mono.error(new RateLimitExceededException("RPM limit exceeded"));
            }

            int currentRpd = requestsToday.incrementAndGet();
            if (currentRpd > rpd) {
                return Mono.error(new RateLimitExceededException("RPD limit exceeded"));
            }

            int currentTpm = tokensThisMinute.addAndGet(estimatedTokens);
            if (currentTpm > tpm) {
                return Mono.error(new RateLimitExceededException("TPM limit exceeded"));
            }

            return Mono.empty();
        });
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}