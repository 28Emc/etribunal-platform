package com.etribunal.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "etribunal.gateway")
public record GatewayAuthProperties(boolean enabled, List<String> publicPaths) {}
