package com.etribunal.ai.automation.infrastructure.auth;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotAuthService {

    private static final Logger log = LoggerFactory.getLogger(BotAuthService.class);

    private final WebClient webClient;
    private final AutomationConfig.BotAuthConfig config;
    private final JdbcTemplate identityJdbcTemplate;
    private final Map<String, BotToken> tokenCache = new ConcurrentHashMap<>();

    public BotAuthService(WebClient.Builder webClientBuilder,
                          AutomationConfig automationConfig,
                          @Qualifier("identityJdbcTemplate") JdbcTemplate identityJdbcTemplate) {
        this.config = automationConfig.getBotAuth();
        this.identityJdbcTemplate = identityJdbcTemplate;
        this.webClient = webClientBuilder
                .baseUrl(config.getIdentityUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Token para un bot indexado 1..N según el email pattern configurado.
     */
    public String getToken(int botIndex) {
        String email = String.format(config.getEmailPattern(), botIndex);
        return getTokenByEmail(email);
    }

    /**
     * Token para un bot identificado por su userId (UUID en identity/común).
     * Resuelve el email del bot en la DB de identity para poder loguearlo.
     * Requiere que el usuario exista en identity con is_bot = true.
     */
    public String getTokenForBot(String botUserId) {
        String email = findBotEmail(botUserId);
        return getTokenByEmail(email);
    }

    private String findBotEmail(String botUserId) {
        List<String> emails = identityJdbcTemplate.queryForList(
                "SELECT email FROM users WHERE id = ? AND is_bot = true AND deleted_at IS NULL",
                String.class, botUserId);
        if (emails.isEmpty()) {
            throw new IllegalStateException("No se encontró un bot (is_bot=true) para el usuario " + botUserId);
        }
        return emails.get(0);
    }

    public void invalidateToken(String email) {
        tokenCache.remove(email);
    }

    private String getTokenByEmail(String email) {
        BotToken cached = tokenCache.get(email);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.accessToken();
        }
        BotToken fresh = loginAndCache(email, config.getPassword());
        tokenCache.put(email, fresh);
        return fresh.accessToken();
    }

    private BotToken loginAndCache(String email, String password) {
        try {
            LoginRequest request = new LoginRequest(email, password);
            LoginResponse response = webClient.post()
                    .uri("/auth/login")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LoginResponse.class)
                    .block(Duration.ofSeconds(config.getHttpTimeoutSeconds()));

            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("Login failed for " + email + ": empty response");
            }

            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn() != null ? response.expiresIn() : config.getTokenCacheTtlMinutes() * 60L);
            log.debug("Bot {} logged in, token expires at {}", email, expiresAt);
            return new BotToken(response.accessToken(), expiresAt);

        } catch (WebClientResponseException e) {
            log.error("Login failed for {}: {} - {}", email, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Login failed for " + email, e);
        }
    }

    // DTOs
    record LoginRequest(String email, String password) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {}
    record BotToken(String accessToken, Instant expiresAt) {}
}