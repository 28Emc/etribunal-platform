package com.etribunal.gateway.migration;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Redis-backed feature flags for Strangler Fig migration.
 *
 * Key pattern: migration:canary:{service}:{route} → percentage (0-100)
 * Example:     migration:canary:core-domain:cases-feed → "10"
 *
 * Percentage means: X% of requests route to Spring, (100-X)% to NestJS.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private static final String KEY_PREFIX = "migration:canary:";

    private final ReactiveStringRedisTemplate redis;
    private final MigrationProperties properties;

    public FeatureFlagService(ReactiveStringRedisTemplate redis,
                              MigrationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Check if a request should route to Spring (canary) or NestJS (legacy).
     * Returns true if this request should go to Spring.
     */
    public Mono<Boolean> shouldRouteToSpring(String service, String route) {
        if (!properties.enabled() || !properties.canary().enabled()) {
            return Mono.just(false);
        }

        return getPercentage(service, route)
                .map(percentage -> {
                    if (percentage <= 0) return false;
                    if (percentage >= 100) return true;
                    boolean routeToSpring = ThreadLocalRandom.current().nextInt(100) < percentage;
                    log.debug("Canary decision: {} {} → {}% → spring={}",
                            service, route, percentage, routeToSpring);
                    return routeToSpring;
                })
                .defaultIfEmpty(false);
    }

    /**
     * Get the canary percentage for a service/route from Redis.
     * Falls back to default percentage if not set.
     */
    public Mono<Integer> getPercentage(String service, String route) {
        String key = KEY_PREFIX + service + ":" + route;
        return redis.opsForValue().get(key)
                .map(Integer::parseInt)
                .onErrorResume(e -> Mono.empty())
                .switchIfEmpty(Mono.just(properties.canary().defaultPercentage()));
    }

    /**
     * Set the canary percentage for a service/route.
     * Use 0 to disable (all NestJS), 100 to fully migrate (all Spring).
     */
    public Mono<Boolean> setPercentage(String service, String route, int percentage) {
        String key = KEY_PREFIX + service + ":" + route;
        int clamped = Math.max(0, Math.min(100, percentage));
        return redis.opsForValue().set(key, String.valueOf(clamped))
                .map(v -> {
                    log.info("Canary flag set: {} {} → {}%", service, route, clamped);
                    return true;
                });
    }

    /**
     * Get the NestJS backend URL for a given route.
     */
    public String getNestJsUrl() {
        return properties.nestjsUrl();
    }
}
