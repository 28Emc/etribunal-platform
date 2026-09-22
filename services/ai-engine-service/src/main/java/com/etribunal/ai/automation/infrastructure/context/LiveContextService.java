package com.etribunal.ai.automation.infrastructure.context;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contexto vivo (Fase 3): construye un bloque de contexto temporal para el prompt
 * de generación de casos: fecha/estación, eventos fijos del calendario y noticias
 * RSS recientes. Robusto: si el feed cae o no está configurado, el motor continúa
 * solo con fecha/estación/eventos.
 */
@Service
public class LiveContextService {

    private static final Logger log = LoggerFactory.getLogger(LiveContextService.class);
    private static final Locale ES = new Locale("es", "ES");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Duration RSS_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_TITLE_CHARS = 120;

    private static final List<SeasonalEvent> SEASONAL_EVENTS = List.of(
            new SeasonalEvent(1, 1, "Año Nuevo"),
            new SeasonalEvent(1, 6, "Día de Reyes"),
            new SeasonalEvent(2, 14, "San Valentín"),
            new SeasonalEvent(3, 8, "Día Internacional de la Mujer"),
            new SeasonalEvent(4, 1, "Día de los Inocentes"),
            new SeasonalEvent(5, 1, "Día del Trabajador"),
            new SeasonalEvent(6, 21, "Comienzo del verano"),
            new SeasonalEvent(7, 4, "Día de la Independencia de EE.UU."),
            new SeasonalEvent(8, 15, "Vacaciones de verano"),
            new SeasonalEvent(9, 21, "Vuelta al cole / Otoño"),
            new SeasonalEvent(10, 31, "Halloween"),
            new SeasonalEvent(11, 29, "Black Friday"),
            new SeasonalEvent(12, 6, "Día de la Constitución"),
            new SeasonalEvent(12, 25, "Navidad"),
            new SeasonalEvent(12, 31, "Nochevieja")
    );

    private final AutomationConfig.ContextConfig contextConfig;
    private final HttpClient httpClient;
    private final ReentrantLock cacheLock = new ReentrantLock();

    private List<String> cachedHeadlines = List.of();
    private Instant cacheExpiry = Instant.EPOCH;

    public LiveContextService(AutomationConfig config) {
        this.contextConfig = config.getContext();
        this.httpClient = HttpClient.newBuilder().connectTimeout(RSS_TIMEOUT).build();
    }

    public String buildContext() {
        return buildContextFor(LocalDate.now(ZONE));
    }

    String buildContextFor(LocalDate today) {
        StringBuilder sb = new StringBuilder();

        appendDateAndSeason(sb, today);
        appendEvents(sb, today);
        appendNews(sb);

        return sb.toString();
    }

    private void appendDateAndSeason(StringBuilder sb, LocalDate today) {
        String dateLabel = today.getDayOfWeek().getDisplayName(TextStyle.FULL, ES)
                + " " + today.getDayOfMonth()
                + " de " + today.getMonth().getDisplayName(TextStyle.FULL, ES)
                + " de " + today.getYear();
        sb.append("Fecha actual: ").append(dateLabel).append(".\n");
        sb.append("Estación: ").append(seasonFor(today).label).append(".\n");
    }

    private void appendEvents(StringBuilder sb, LocalDate today) {
        List<String> current = new ArrayList<>();
        List<String> upcoming = new ArrayList<>();
        for (SeasonalEvent ev : SEASONAL_EVENTS) {
            if (ev.getMonthDay().equals(MonthDay.from(today))) {
                current.add(ev.name());
            } else {
                LocalDate next = nextOccurrence(ev, today);
                long delta = ChronoUnit.DAYS.between(today, next);
                if (delta >= 1 && delta <= 30) {
                    upcoming.add(ev.name() + " (en " + delta + " días, " + next + ")");
                }
            }
        }
        if (!current.isEmpty()) {
            sb.append("Hoy: ").append(String.join(", ", current)).append(".\n");
        }
        if (!upcoming.isEmpty()) {
            sb.append("Próximos eventos (pueden inspirar debate): ").append(String.join("; ", upcoming)).append(".\n");
        }
    }

    private void appendNews(StringBuilder sb) {
        List<String> feedUrls = contextConfig.isNewsEnabled() ? contextConfig.getRssFeedUrls() : List.of();
        if (feedUrls.isEmpty()) {
            return;
        }
        List<String> headlines;
        cacheLock.lock();
        try {
            if (Instant.now().isBefore(cacheExpiry)) {
                headlines = cachedHeadlines;
            } else {
                headlines = fetchHeadlines(feedUrls);
                cachedHeadlines = headlines;
                cacheExpiry = Instant.now().plus(contextConfig.getNewsCacheTtl());
            }
        } finally {
            cacheLock.unlock();
        }
        if (headlines.isEmpty()) {
            return;
        }
        sb.append("Noticias recientes (úsalas solo si aportan un ángulo interesante para el debate):\n");
        for (String h : headlines) {
            sb.append("- ").append(h).append("\n");
        }
    }

    private List<String> fetchHeadlines(List<String> feedUrls) {
        List<String> headlines = new ArrayList<>();
        int max = Math.max(1, contextConfig.getMaxNewsItems());
        for (String url : feedUrls) {
            if (headlines.size() >= max) {
                break;
            }
            try {
                headlines.addAll(parseRss(fetch(url)));
            } catch (Exception e) {
                log.warn("RSS feed {} failed: {}", url, e.getMessage());
            }
        }
        return headlines.stream().distinct().limit(max).toList();
    }

    private String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(RSS_TIMEOUT)
                .header("User-Agent", "eTribunal-AI-Engine")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RSS request interrupted", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> parseRss(String xml) {
        List<String> headlines = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return headlines;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            addTitles(doc.getElementsByTagName("item"), headlines);
            if (headlines.isEmpty()) {
                addTitles(doc.getElementsByTagName("entry"), headlines);
            }
        } catch (Exception e) {
            log.warn("Could not parse RSS XML: {}", e.getMessage());
        }
        return headlines;
    }

    private void addTitles(NodeList nodes, List<String> out) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String title = extractTitle((Element) node);
            if (title != null) {
                out.add(title);
            }
        }
    }

    private String extractTitle(Element el) {
        NodeList titleNodes = el.getElementsByTagName("title");
        if (titleNodes.getLength() == 0) {
            return null;
        }
        String title = titleNodes.item(0).getTextContent().trim();
        if (title.isBlank()) {
            return null;
        }
        if (title.length() > MAX_TITLE_CHARS) {
            title = title.substring(0, MAX_TITLE_CHARS) + "…";
        }
        return title;
    }

    private LocalDate nextOccurrence(SeasonalEvent ev, LocalDate today) {
        LocalDate candidate = today.withMonth(ev.month()).withDayOfMonth(ev.day());
        if (!candidate.isAfter(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }

    private Season seasonFor(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        if ((m == 12 && d >= 21) || m <= 2 || (m == 3 && d < 21)) {
            return Season.WINTER;
        }
        if (m <= 5 || (m == 6 && d < 21)) {
            return Season.SPRING;
        }
        if (m <= 8 || (m == 9 && d < 21)) {
            return Season.SUMMER;
        }
        return Season.AUTUMN;
    }

    private enum Season {
        WINTER("invierno"), SPRING("primavera"), SUMMER("verano"), AUTUMN("otoño");
        private final String label;
        Season(String label) { this.label = label; }
    }

    private record SeasonalEvent(int month, int day, String name) {
        private MonthDay getMonthDay() { return MonthDay.of(month, day); }
    }
}
