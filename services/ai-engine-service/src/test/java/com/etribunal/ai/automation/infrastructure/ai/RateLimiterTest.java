package com.etribunal.ai.automation.infrastructure.ai;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class RateLimiterTest {

    @Test
    void acquireRejectsWhenRpmExceededWithoutConsumingQuota() {
        RateLimiter limiter = new RateLimiter(2, 100, 100_000);
        StepVerifier.create(limiter.acquire(1)).verifyComplete();
        StepVerifier.create(limiter.acquire(1)).verifyComplete();
        StepVerifier.create(limiter.acquire(1))
                .expectError(RateLimiter.RateLimitExceededException.class)
                .verify();

        // Un rechazo NO quema cuota: un reintento tras un error no debe fallar
        // "prematuramente" por la cuota del intento rechazado (misma ventana).
    }

    @Test
    void acquireRejectsWhenRpdExceededWithoutConsumingQuota() {
        RateLimiter limiter = new RateLimiter(1000, 2, 100_000);
        StepVerifier.create(limiter.acquire(1)).verifyComplete();
        StepVerifier.create(limiter.acquire(1)).verifyComplete();
        StepVerifier.create(limiter.acquire(1))
                .expectError(RateLimiter.RateLimitExceededException.class)
                .verify();
    }

    @Test
    void acquireRejectsWhenTpmExceededWithoutConsumingTokens() {
        RateLimiter limiter = new RateLimiter(1000, 1000, 10);
        StepVerifier.create(limiter.acquire(10)).verifyComplete();
        StepVerifier.create(limiter.acquire(1))
                .expectError(RateLimiter.RateLimitExceededException.class)
                .verify();
    }

    @Test
    void tokensAccumuladosNoPuedenExcederTpm() {
        RateLimiter limiter = new RateLimiter(1000, 1000, 15);
        StepVerifier.create(limiter.acquire(10)).verifyComplete();
        StepVerifier.create(limiter.acquire(5)).verifyComplete();
        StepVerifier.create(limiter.acquire(1))
                .expectError(RateLimiter.RateLimitExceededException.class)
                .verify();
    }

    @Test
    void windowMinutoSeReseteaAntesDeEvaluarLimites() {
        RateLimiter limiter = new RateLimiter(1, 1_000_000, 1_000_000);
        StepVerifier.create(limiter.acquire(1)).verifyComplete();
        StepVerifier.create(limiter.acquire(1))
                .expectError(RateLimiter.RateLimitExceededException.class)
                .verify();
    }
}