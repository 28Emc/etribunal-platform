package com.etribunal.ai.automation.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

class BotAuthServiceTest {

    private HttpServer server;
    private BotAuthService service;
    private JdbcTemplate identityJdbcTemplate;
    private String loginBody;
    private int loginStatus = 200;
    private int loginCalls = 0;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/api/auth/login", exchange -> {
            loginCalls++;
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = (loginBody == null ? "" : loginBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(loginStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        identityJdbcTemplate = mock(JdbcTemplate.class);

        AutomationConfig config = new AutomationConfig();
        config.getBotAuth().setIdentityUrl("http://localhost:" + server.getAddress().getPort() + "/api");
        config.getBotAuth().setPassword("secret");
        config.getBotAuth().setEmailPattern("bot%02d@etsocial.local");
        config.getBotAuth().setHttpTimeoutSeconds(5);
        config.getBotAuth().setTokenCacheTtlMinutes(30);

        service = new BotAuthService(WebClient.builder(), config, identityJdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static String loginJson(String accessToken) {
        return "{\"data\":{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"r\",\"expires_in\":3600}}";
    }

    @Test
    void getToken_formatsEmail_andLogsIn() {
        loginBody = loginJson("token-1");

        String token = service.getToken(1);

        assertThat(token).isEqualTo("token-1");
        assertThat(loginCalls).isEqualTo(1);
    }

    @Test
    void getToken_cachesToken_secondCallSkipsLogin() {
        loginBody = loginJson("token-cached");

        String first = service.getToken(1);
        String second = service.getToken(1);

        assertThat(first).isEqualTo("token-cached");
        assertThat(second).isEqualTo("token-cached");
        assertThat(loginCalls).isEqualTo(1);
    }

    @Test
    void invalidateToken_forcesNewLogin() {
        loginBody = loginJson("t1");
        service.getToken(1);
        assertThat(loginCalls).isEqualTo(1);

        service.invalidateToken(String.format("bot%02d@etsocial.local", 1));
        loginBody = loginJson("t2");
        String token = service.getToken(1);

        assertThat(token).isEqualTo("t2");
        assertThat(loginCalls).isEqualTo(2);
    }

    @Test
    void getTokenForBot_resolvesEmailFromDb() {
        UUID botId = UUID.randomUUID();
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), eq(botId)))
                .thenReturn(List.of("bot01@etsocial.local"));
        loginBody = loginJson("bot-token");

        String token = service.getTokenForBot(botId.toString());

        assertThat(token).isEqualTo("bot-token");
    }

    @Test
    void getTokenForBot_invalidUuid_throws() {
        assertThatThrownBy(() -> service.getTokenForBot("not-a-uuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no es un UUID");
    }

    @Test
    void getTokenForBot_noBotFound_throws() {
        UUID botId = UUID.randomUUID();
        String rawId = botId.toString();
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), eq(botId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getTokenForBot(rawId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se encontr");
    }

    @Test
    void login_emptyResponse_throws() {
        String rawId = UUID.randomUUID().toString();
        loginBody = "{\"data\":null}";
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of("bot@x.com"));

        assertThatThrownBy(() -> service.getTokenForBot(rawId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void login_nullAccessToken_throws() {
        String rawId = UUID.randomUUID().toString();
        loginBody = "{\"data\":{\"access_token\":null,\"expires_in\":100}}";
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of("bot@x.com"));

        assertThatThrownBy(() -> service.getTokenForBot(rawId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Login failed");
    }

    @Test
    void login_httpError_throwsIllegalState() {
        String rawId = UUID.randomUUID().toString();
        loginBody = "{\"error\":\"bad\"}";
        loginStatus = 401;
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of("bot@x.com"));

        assertThatThrownBy(() -> service.getTokenForBot(rawId))
                .isInstanceOf(IllegalStateException.class);
        loginStatus = 200;
    }

    @Test
    void login_nullExpiresIn_usesConfigTtl() {
        String rawId = UUID.randomUUID().toString();
        loginBody = "{\"data\":{\"access_token\":\"tok-no-exp\",\"expires_in\":null}}";
        when(identityJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of("bot@x.com"));

        String token = service.getTokenForBot(rawId);

        assertThat(token).isEqualTo("tok-no-exp");
    }
}
