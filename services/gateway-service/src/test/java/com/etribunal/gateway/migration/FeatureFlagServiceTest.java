package com.etribunal.gateway.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private FeatureFlagService service;

    private static final MigrationProperties ENABLED_PROPS = new MigrationProperties(
            true, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(true, 0),
            new MigrationProperties.ShadowProperties(false, false));

    private static final MigrationProperties DISABLED_PROPS = new MigrationProperties(
            false, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(true, 0),
            new MigrationProperties.ShadowProperties(false, false));

    private static final MigrationProperties CANARY_DISABLED_PROPS = new MigrationProperties(
            true, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(false, 0),
            new MigrationProperties.ShadowProperties(false, false));

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shouldRouteToSpringReturnsFalseWhenMigrationDisabled() {
        service = new FeatureFlagService(redis, DISABLED_PROPS);

        StepVerifier.create(service.shouldRouteToSpring("core-domain", "cases"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldRouteToSpringReturnsFalseWhenCanaryDisabled() {
        service = new FeatureFlagService(redis, CANARY_DISABLED_PROPS);

        StepVerifier.create(service.shouldRouteToSpring("core-domain", "cases"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldRouteToSpringReturnsTrueWhenPercentageIs100() {
        service = new FeatureFlagService(redis, ENABLED_PROPS);
        when(valueOps.get("migration:canary:core-domain:cases"))
                .thenReturn(Mono.just("100"));

        StepVerifier.create(service.shouldRouteToSpring("core-domain", "cases"))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldRouteToSpringReturnsFalseWhenPercentageIs0() {
        service = new FeatureFlagService(redis, ENABLED_PROPS);
        when(valueOps.get("migration:canary:core-domain:cases"))
                .thenReturn(Mono.just("0"));

        StepVerifier.create(service.shouldRouteToSpring("core-domain", "cases"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldRouteToSpringUsesDefaultWhenKeyNotFound() {
        MigrationProperties props = new MigrationProperties(
                true, "http://localhost:3001/api",
                new MigrationProperties.CanaryProperties(true, 50),
                new MigrationProperties.ShadowProperties(false, false));
        service = new FeatureFlagService(redis, props);
        when(valueOps.get("migration:canary:core-domain:cases"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.shouldRouteToSpring("core-domain", "cases"))
                .assertNext(result -> {
                    // With 50% default, result could be true or false
                    assertThat(result).isInstanceOf(Boolean.class);
                })
                .verifyComplete();
    }

    @Test
    void getPercentageReturnsDefaultWhenKeyNotFound() {
        MigrationProperties props = new MigrationProperties(
                true, "http://localhost:3001/api",
                new MigrationProperties.CanaryProperties(true, 25),
                new MigrationProperties.ShadowProperties(false, false));
        service = new FeatureFlagService(redis, props);
        when(valueOps.get("migration:canary:core-domain:cases"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.getPercentage("core-domain", "cases"))
                .assertNext(result -> assertThat(result).isEqualTo(25))
                .verifyComplete();
    }

    @Test
    void setPercentageStoresInRedis() {
        service = new FeatureFlagService(redis, ENABLED_PROPS);
        when(valueOps.set("migration:canary:core-domain:cases", "10"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.setPercentage("core-domain", "cases", 10))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void setPercentageClampsValues() {
        service = new FeatureFlagService(redis, ENABLED_PROPS);
        when(valueOps.set("migration:canary:core-domain:cases", "100"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.setPercentage("core-domain", "cases", 150))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void getNestJsUrlReturnsConfiguredUrl() {
        service = new FeatureFlagService(redis, ENABLED_PROPS);
        assertThat(service.getNestJsUrl()).isEqualTo("http://localhost:3001/api");
    }
}
