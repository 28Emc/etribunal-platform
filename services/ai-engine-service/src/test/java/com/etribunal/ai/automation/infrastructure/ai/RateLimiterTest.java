package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

class RateLimiterTest {

    @Test
    void acquire_allowsRequest_whenWithinLimits() {
        RateLimiter limiter = new RateLimiter(10, 100, 1000);

        StepVerifier.create(limiter.acquire(1))
                .verifyComplete();

        StepVerifier.create(limiter.acquire(5))
                .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("exceededLimitCases")
    void acquire_rejects_whenLimitExceeded(int rpm, int rpd, int tpm, int tokens, int allowedCalls) {
        RateLimiter limiter = new RateLimiter(rpm, rpd, tpm);

        for (int i = 0; i < allowedCalls; i++) {
            StepVerifier.create(limiter.acquire(tokens)).verifyComplete();
        }
        StepVerifier.create(limiter.acquire(tokens))
                .expectErrorMatches(e -> e instanceof RateLimiter.RateLimitExceededException)
                .verify();
    }

    static Stream<Arguments> exceededLimitCases() {
        return Stream.of(
                Arguments.of(2, 100, 1000, 1, 2),
                Arguments.of(100, 2, 1000, 1, 2),
                Arguments.of(100, 100, 5, 3, 1)
        );
    }

    @Test
    void acquire_rejectsZeroTokens() {
        RateLimiter limiter = new RateLimiter(10, 100, 1000);

        StepVerifier.create(limiter.acquire(0)).verifyComplete();
    }

    @Test
    void acquire_rejectsNegativeTokens() {
        RateLimiter limiter = new RateLimiter(10, 100, 1000);

        StepVerifier.create(limiter.acquire(-1)).verifyComplete();
    }

    @Test
    void rateLimitExceededException_hasMessage() {
        RateLimiter.RateLimitExceededException ex = new RateLimiter.RateLimitExceededException("test message");

        assertThat(ex.getMessage()).isEqualTo("test message");
    }
}
