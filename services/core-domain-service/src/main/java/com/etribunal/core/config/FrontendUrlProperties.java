package com.etribunal.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "etribunal.frontend")
public record FrontendUrlProperties(String url) {

    public String inviteUrl(String token) {
        String base = url != null ? url.replaceAll("/$", "") : "";
        return base + "/case/" + token;
    }
}
