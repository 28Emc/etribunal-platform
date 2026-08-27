package com.etribunal.gateway.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "etribunal.migration")
public record MigrationProperties(
        boolean enabled,
        String nestjsUrl,
        CanaryProperties canary,
        ShadowProperties shadow
) {
    public record CanaryProperties(
            boolean enabled,
            int defaultPercentage
    ) {
    }

    public record ShadowProperties(
            boolean enabled,
            boolean logDifferences
    ) {
    }
}
