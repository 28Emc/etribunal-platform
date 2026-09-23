package com.etribunal.ai.automation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

class CoreApiClientTest {

    private HttpServer server;
    private CoreApiClient client;
    private String nextBody;
    private int nextStatus = 200;
    private String lastPath = "";
    private String lastMethod = "";
    private String lastBody = "";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/", exchange -> {
            lastMethod = exchange.getRequestMethod();
            lastPath = exchange.getRequestURI().getPath();
            if (exchange.getRequestBody() != null) {
                lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            }
            byte[] bytes = (nextBody == null ? "" : nextBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(nextStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        AutomationConfig config = new AutomationConfig();
        config.getBotAuth().setCoreUrl("http://localhost:" + server.getAddress().getPort() + "/api");
        config.getBotAuth().setHttpTimeoutSeconds(5);
        client = new CoreApiClient(WebClient.builder(), config);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static String api(String dataJson) {
        return "{\"data\":" + dataJson + ",\"message\":\"ok\",\"statusCode\":200}";
    }

    @Test
    void vote_returnsCaseId() {
        UUID caseId = UUID.randomUUID();
        nextBody = api("{\"case_id\":\"" + caseId + "\",\"vote_type\":\"A\",\"votes_a\":1,\"votes_b\":0,\"votes_both_wrong\":0}");

        UUID result = client.vote("tok", caseId, "A");

        assertThat(result).isEqualTo(caseId);
        assertThat(lastMethod).isEqualTo("POST");
        assertThat(lastPath).contains("/votes");
        assertThat(lastBody).contains("vote_type");
    }

    @Test
    void vote_emptyResponse_throws() {
        nextBody = "{\"data\":null,\"message\":\"\",\"statusCode\":200}";
        UUID caseId = UUID.randomUUID();

        assertThatThrownBy(() -> client.vote("tok", caseId, "A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vote: empty response");
    }

    @Test
    void vote_nullResponse_throws() {
        nextBody = "";
        nextStatus = 500;
        UUID caseId = UUID.randomUUID();

        assertThatThrownBy(() -> client.vote("tok", caseId, "A"))
                .isInstanceOf(Exception.class);
        nextStatus = 200;
    }

    @Test
    void createComment_returnsCommentId() {
        UUID commentId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        nextBody = api("{\"id\":\"" + commentId + "\"}");

        UUID result = client.createComment("tok", caseId, "content", null, false);

        assertThat(result).isEqualTo(commentId);
        assertThat(lastMethod).isEqualTo("POST");
        assertThat(lastPath).contains("/comments");
        assertThat(lastBody).contains("content", "is_anonymous");
    }

    @Test
    void createComment_withParentId_includesParent() {
        UUID commentId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        nextBody = api("{\"id\":\"" + commentId + "\"}");

        UUID result = client.createComment("tok", caseId, "content", parentId, true);

        assertThat(result).isEqualTo(commentId);
        assertThat(lastBody).contains(parentId.toString());
    }

    @Test
    void createComment_emptyResponse_throws() {
        nextBody = "{\"data\":null}";
        UUID caseId = UUID.randomUUID();

        assertThatThrownBy(() -> client.createComment("tok", caseId, "c", null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createComment: empty response");
    }

    @Test
    void addReaction_returnsDeterministicUuid() {
        UUID targetId = UUID.randomUUID();
        nextBody = api("{\"reactions\":[{\"emoji\":\"LIKE\",\"count\":1}],\"user_reaction\":\"LIKE\"}");

        UUID result = client.addReaction("tok", "CASE", targetId, "LIKE");
        UUID expected = UUID.nameUUIDFromBytes(("CASE:" + targetId + ":LIKE").getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(expected);
        assertThat(lastPath).contains("/reactions");
    }

    @Test
    void deleteVote_succeeds() {
        nextBody = "";
        nextStatus = 204;
        UUID caseId = UUID.randomUUID();

        client.deleteVote("tok", caseId);

        assertThat(lastMethod).isEqualTo("DELETE");
        assertThat(lastPath).contains("/votes");
        nextStatus = 200;
    }

    @Test
    void deleteComment_succeeds() {
        nextBody = "";
        nextStatus = 204;
        UUID commentId = UUID.randomUUID();

        client.deleteComment("tok", commentId);

        assertThat(lastMethod).isEqualTo("DELETE");
        assertThat(lastPath).contains("/comments");
        nextStatus = 200;
    }

    @Test
    void removeReaction_succeeds() {
        nextBody = "";
        nextStatus = 204;
        UUID targetId = UUID.randomUUID();

        client.removeReaction("tok", "CASE", targetId, "LIKE");

        assertThat(lastMethod).isEqualTo("DELETE");
        assertThat(lastPath).contains("/reactions");
        nextStatus = 200;
    }
}
