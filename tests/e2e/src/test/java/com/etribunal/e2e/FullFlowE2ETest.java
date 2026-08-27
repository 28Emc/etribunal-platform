package com.etribunal.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests against running services (identity:8081, core:8082 via gateway:8080).
 * Run services first: `docker compose up` or bootRun each service.
 *
 * Usage: ./gradlew :tests:e2e:test -Dgateway.url=http://localhost:8080
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("E2E: Full user flow through gateway")
class FullFlowE2ETest {

    private static final String GATEWAY = System.getProperty("gateway.url", "http://localhost:8080");
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String accessToken;
    private static String refreshToken;
    private static String userId;
    private static String caseId;

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> del(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    // ──────────────── AUTH ────────────────

    @Test
    @Order(1)
    @DisplayName("Register user")
    void register() throws Exception {
        HttpResponse<String> res = post("/api/auth/register", """
                {
                    "username": "e2e_flow",
                    "email": "e2e-flow@etribunal.test",
                    "password": "Test1234!",
                    "displayName": "E2E Flow User"
                }
                """);

        assertThat(res.statusCode()).isIn(200, 201);
    }

    @Test
    @Order(2)
    @DisplayName("Login and get tokens")
    void login() throws Exception {
        HttpResponse<String> res = post("/api/auth/login", """
                {
                    "email": "e2e-flow@etribunal.test",
                    "password": "Test1234!"
                }
                """);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("access_token");

        // Extract tokens (simple substring extraction for test)
        accessToken = extractJson(res.body(), "access_token");
        refreshToken = extractJson(res.body(), "refresh_token");
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("Get profile with token")
    void getProfile() throws Exception {
        HttpResponse<String> res = get("/api/users/me", accessToken);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("e2e_flow");
        userId = extractJson(res.body(), "id");
    }

    @Test
    @Order(4)
    @DisplayName("Refresh token")
    void refresh() throws Exception {
        HttpResponse<String> res = post("/api/auth/refresh", """
                {
                    "refresh_token": "%s"
                }
                """.formatted(refreshToken));

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("access_token");
    }

    @Test
    @Order(5)
    @DisplayName("Reject invalid token")
    void rejectInvalid() throws Exception {
        HttpResponse<String> res = get("/api/users/me", "invalid-token");
        assertThat(res.statusCode()).isIn(401, 403);
    }

    // ──────────────── CASES ────────────────

    @Test
    @Order(10)
    @DisplayName("Create vote case")
    void createCase() throws Exception {
        HttpResponse<String> res = post("/api/cases", """
                {
                    "type": "vote",
                    "title": "E2E: Is water wet?",
                    "side_a_content": "Yes, water is inherently wet",
                    "side_b_content": "No, water makes things wet but isn't wet itself",
                    "category": "Science"
                }
                """);

        assertThat(res.statusCode()).isIn(200, 201);
        caseId = extractJson(res.body(), "id");
        assertThat(caseId).isNotBlank();
    }

    @Test
    @Order(11)
    @DisplayName("Get case by ID")
    void getCase() throws Exception {
        HttpResponse<String> res = get("/api/cases/" + caseId, accessToken);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("Is water wet?");
    }

    @Test
    @Order(12)
    @DisplayName("Vote on case")
    void voteCase() throws Exception {
        HttpResponse<String> res = post("/api/cases/" + caseId + "/votes", """
                {
                    "vote_type": "A"
                }
                """);

        assertThat(res.statusCode()).isIn(200, 201);
    }

    @Test
    @Order(13)
    @DisplayName("Remove vote")
    void removeVote() throws Exception {
        HttpResponse<String> res = del("/api/cases/" + caseId + "/votes", accessToken);

        assertThat(res.statusCode()).isIn(200, 204);
    }

    @Test
    @Order(14)
    @DisplayName("Save case")
    void saveCase() throws Exception {
        HttpResponse<String> res = post("/api/cases/" + caseId + "/save", "");

        assertThat(res.statusCode()).isIn(200, 201);
    }

    @Test
    @Order(15)
    @DisplayName("Unsave case")
    void unsaveCase() throws Exception {
        HttpResponse<String> res = del("/api/cases/" + caseId + "/save", accessToken);

        assertThat(res.statusCode()).isIn(200, 204);
    }

    @Test
    @Order(16)
    @DisplayName("Get feed")
    void getFeed() throws Exception {
        HttpResponse<String> res = get("/api/cases?skip=0&take=5", accessToken);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("cases");
    }

    // ──────────────── HELPERS ────────────────

    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            search = "\"" + key + "\": \"";
            start = json.indexOf(search);
        }
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}