package com.etribunal.ai.automation.infrastructure.ai;

import java.time.Instant;
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
        return Mono.defer(() -> tryAcquire(estimatedTokens)
                ? Mono.empty()
                : Mono.error(new RateLimitExceededException(
                        "Límite de Gemini alcanzado, reintenta en un momento")));
    }

    /**
     * Reserva la cuota de forma atómica: primero comprueba límites y SOLO
     * incrementa los contadores si la petición cabe. Un rechazo no consume
     * cuota (no quema el presupuesto al reintentar).
     */
    private synchronized boolean tryAcquire(int estimatedTokens) {
        long now = Instant.now().toEpochMilli();

        resetMinuteWindowIfNeeded(now);
        resetDayWindowIfNeeded(now);

        if (requestsThisMinute.get() >= rpm) {
            return false;
        }
        if (requestsToday.get() >= rpd) {
            return false;
        }
        if (tokensThisMinute.get() + estimatedTokens > tpm) {
            return false;
        }

        requestsThisMinute.incrementAndGet();
        requestsToday.incrementAndGet();
        tokensThisMinute.addAndGet(estimatedTokens);
        return true;
    }

    private void resetMinuteWindowIfNeeded(long now) {
        long minuteStart = minuteWindowStart.get();
        if (now - minuteStart >= 60_000 && minuteWindowStart.compareAndSet(minuteStart, now)) {
            requestsThisMinute.set(0);
            tokensThisMinute.set(0);
            tokenWindowStart.set(now);
        }
    }

    private void resetDayWindowIfNeeded(long now) {
        long dayStart = dayWindowStart.get();
        if (now - dayStart >= 86_400_000 && dayWindowStart.compareAndSet(dayStart, now)) {
            requestsToday.set(0);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}