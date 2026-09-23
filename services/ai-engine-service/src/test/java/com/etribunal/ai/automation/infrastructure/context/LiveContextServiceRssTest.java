package com.etribunal.ai.automation.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;

class LiveContextServiceRssTest {

    private HttpServer server;
    private AutomationConfig config;
    private String rssBody;
    private int rssStatus = 200;
    private int rssCalls = 0;

    private static final String RSS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Feed</title>
                <item><title>Headline One</title></item>
                <item><title>%s</title></item>
                <item><title>Duplicate</title></item>
                <item><title>Duplicate</title></item>
              </channel>
            </rss>
            """;

    private static final String ATOM_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Atom Headline</title></entry>
            </feed>
            """;

    private static final String LONG_TITLE = "L".repeat(200);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/rss", exchange -> {
            rssCalls++;
            byte[] bytes = (rssBody == null ? "" : rssBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml");
            exchange.sendResponseHeaders(rssStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        config = new AutomationConfig();
        config.getContext().setNewsEnabled(true);
        config.getContext().setRssFeedUrls(List.of(
                "http://localhost:" + server.getAddress().getPort() + "/rss"));
        config.getContext().setMaxNewsItems(5);
        config.getContext().setNewsCacheTtl(Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void buildContext_withNews_includesHeadlines() {
        rssBody = RSS_XML.formatted("Second Headline");
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContextFor(LocalDate.of(2026, 9, 15));

        assertThat(ctx)
                .contains("Noticias recientes", "Headline One", "Second Headline", "Fecha actual:");
        assertThat(rssCalls).isGreaterThanOrEqualTo(1);
    }

    @Test
    void buildContext_deduplicatesHeadlines() {
        rssBody = RSS_XML.formatted("X");
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        int count = ctx.split("Duplicate", -1).length - 1;
        assertThat(count).isLessThanOrEqualTo(1);
    }

    @Test
    void buildContext_limitsToMaxNewsItems() {
        config.getContext().setMaxNewsItems(2);
        rssBody = RSS_XML.formatted("X");
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx).doesNotContain("Duplicate");
    }

    @Test
    void buildContext_cachesNews_secondCallSkipsFetch() {
        rssBody = RSS_XML.formatted("X");
        LiveContextService svc = new LiveContextService(config);

        svc.buildContext();
        int callsAfterFirst = rssCalls;
        svc.buildContext();

        assertThat(rssCalls).isEqualTo(callsAfterFirst);
    }

    @Test
    void buildContext_atomFeed_parsesEntryTitles() {
        rssBody = ATOM_XML;
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx).contains("Atom Headline");
    }

    @Test
    void buildContext_longTitle_truncated() {
        rssBody = RSS_XML.formatted(LONG_TITLE);
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx)
                .doesNotContain(LONG_TITLE)
                .contains("L".repeat(120));
    }

    @Test
    void buildContext_httpError_skipsNews() {
        rssStatus = 500;
        rssBody = "error";
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx)
                .doesNotContain("Noticias recientes")
                .contains("Fecha actual:");
        rssStatus = 200;
    }

    @Test
    void buildContext_unreachableFeed_skipsNews() {
        config.getContext().setRssFeedUrls(List.of("http://localhost:1/nope"));
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx)
                .doesNotContain("Noticias recientes")
                .contains("Fecha actual:");
    }

    @Test
    void buildContext_blankXml_skipsNews() {
        rssBody = "";
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx).doesNotContain("Noticias recientes");
    }

    @Test
    void buildContext_malformedXml_skipsNews() {
        rssBody = "<<<not xml>>>";
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx).doesNotContain("Noticias recientes");
    }

    @Test
    void buildContext_multipleFeeds_stopsAtMax() {
        config.getContext().setMaxNewsItems(1);
        LiveContextService svc = new LiveContextService(config);

        rssBody = RSS_XML.formatted("OnlyOne");
        String ctx = svc.buildContext();

        assertThat(ctx)
                .contains("Headline One")
                .doesNotContain("OnlyOne");
    }

    @Test
    void buildContext_expiredCache_refetches() throws Exception {
        config.getContext().setNewsCacheTtl(Duration.ofMinutes(15));
        rssBody = RSS_XML.formatted("Fresh");
        LiveContextService svc = new LiveContextService(config);

        svc.buildContext();
        expireNewsCache(svc);
        svc.buildContext();

        assertThat(rssCalls).isGreaterThanOrEqualTo(2);
    }

    private static void expireNewsCache(LiveContextService svc) throws Exception {
        var expiryField = LiveContextService.class.getDeclaredField("cacheExpiry");
        expiryField.setAccessible(true);
        expiryField.set(svc, java.time.Instant.EPOCH);
    }

    @Test
    void buildContextFor_eventsAndSeasons_comprehensive() {
        rssBody = null;
        config.getContext().setNewsEnabled(false);
        LiveContextService svc = new LiveContextService(config);

        assertThat(svc.buildContextFor(LocalDate.of(2026, 2, 14))).contains("San Valent");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 3, 8))).contains("Mujer");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 4, 1))).contains("Inocentes");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 5, 1))).contains("Trabajador");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 6, 21))).contains("verano");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 10, 31))).contains("Halloween");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 11, 29))).contains("Black Friday");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 12, 6))).contains("Constituc");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 12, 31))).contains("Nochevieja");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 1, 6))).contains("Reyes");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 8, 15))).contains("Vacaciones");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 9, 21))).contains("cole");
        assertThat(svc.buildContextFor(LocalDate.of(2026, 7, 4))).contains("Independencia");
    }

    @Test
    void emptyTitleItems_ignored() {
        rssBody = """
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                  <item><title>   </title></item>
                  <item><description>no title</description></item>
                  <item><title>Real</title></item>
                </channel></rss>
                """;
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContext();

        assertThat(ctx)
                .contains("Real")
                .doesNotContain("no title");
    }

    @Test
    void nonElementNodes_ignored() {
        rssBody = """
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                  <!-- comment -->
                  <item><title>Ok</title></item>
                </channel></rss>
                """;
        LiveContextService svc = new LiveContextService(config);

        assertThat(svc.buildContext()).contains("Ok");
    }
}
