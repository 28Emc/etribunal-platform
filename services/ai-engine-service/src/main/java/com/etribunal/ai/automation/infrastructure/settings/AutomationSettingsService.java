package com.etribunal.ai.automation.infrastructure.settings;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuración de negocio híbrida (Fase 4): los parámetros editables del motor
 * viven en la tabla {@code automation_settings} (BD primero), y el entorno
 * (application.yml / env) actúa como fallback inicial.
 *
 * <p>Los datos sensibles (api keys, secrets, urls BD) permanecen en {@code .env} y
 * nunca se guardan en BD. Este servicio aplica las filas de BD sobre el bean
 * {@link AutomationConfig} (que ya trae el baseline de env) y expone/actualiza el
 * mapa de settings para el panel admin.
 */
@Service
public class AutomationSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AutomationSettingsService.class);
    private static final String TABLE = "automation_settings";

    private static final Set<String> BOOL_KEYS = Set.of(
            "enabled", "dryRun", "activityWeighted", "engagementEnabled"
    );

    private static final Set<String> INT_KEYS = Set.of(
            "runHour",
            "dailyCasesMin", "dailyCasesMax",
            "usersPerCaseMin", "usersPerCaseMax",
            "maxInteractionsPerUserPerCaseMin", "maxInteractionsPerUserPerCaseMax",
            "intensityMin", "intensityMax",
            "schedulingIntervalMin", "schedulingIntervalMax",
            "schedulingWindowHours", "dailyPoolSize",
            "engagementTopExamples", "engagementEvaluationDays",
            "engagementVotesWeight", "engagementCommentsWeight", "engagementReactionsWeight",
            "engagementSharesWeight", "engagementSavesWeight", "engagementViewsWeight"
    );

    private static final Set<String> STRING_ARR_KEYS = Set.of("rssFeedUrls");

    private final JdbcTemplate jdbcTemplate;
    private final AutomationConfig config;
    private final ObjectMapper objectMapper;

    public AutomationSettingsService(JdbcTemplate jdbcTemplate, AutomationConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        applyToConfig();
    }

    /** Aplica las filas de BD sobre {@link AutomationConfig}. Idempotente. */
    public synchronized void applyToConfig() {
        List<Map.Entry<String, JsonNode>> rows = readAll();
        boolean any = false;
        for (Map.Entry<String, JsonNode> row : rows) {
            try {
                applyValue(row.getKey(), row.getValue());
                any = true;
            } catch (Exception e) {
                log.warn("Could not apply automation setting '{}': {}", row.getKey(), e.getMessage());
            }
        }
        if (any) {
            log.info("Automation settings applied from DB ({} keys)", rows.size());
        } else {
            log.info("No automation settings in DB; using env defaults");
        }
    }

    /** Devuelve el payload completo de settings (efectivos, BD + env como baseline). */
    public Map<String, Object> getSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config.isEnabled());
        result.put("dryRun", config.isDryRun());
        result.put("runHour", config.getRunHour());
        result.put("language", config.getLanguage());
        result.put("dailyCasesMin", config.getDailyCasesMin());
        result.put("dailyCasesMax", config.getDailyCasesMax());
        result.put("usersPerCaseMin", config.getUsersPerCaseMin());
        result.put("usersPerCaseMax", config.getUsersPerCaseMax());
        result.put("maxInteractionsPerUserPerCaseMin", config.getMaxInteractionsPerUserPerCaseMin());
        result.put("maxInteractionsPerUserPerCaseMax", config.getMaxInteractionsPerUserPerCaseMax());
        result.put("intensityMin", config.getIntensityMin());
        result.put("intensityMax", config.getIntensityMax());
        result.put("schedulingIntervalMin", config.getSchedulingIntervalMin());
        result.put("schedulingIntervalMax", config.getSchedulingIntervalMax());
        result.put("schedulingWindowHours", config.getSchedulingWindowHours());
        result.put("dailyPoolSize", config.getDailyPoolSize());
        result.put("activityWeighted", config.getActivity().isWeighted());
        result.put("engagementEnabled", config.getEngagement().isEnabled());
        AutomationConfig.EngagementConfig e = config.getEngagement();
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("votes", e.getVotesWeight());
        weights.put("comments", e.getCommentsWeight());
        weights.put("reactions", e.getReactionsWeight());
        weights.put("shares", e.getSharesWeight());
        weights.put("saves", e.getSavesWeight());
        weights.put("views", e.getViewsWeight());
        result.put("engagementWeights", weights);
        result.put("engagementTopExamples", e.getTopExamples());
        result.put("engagementEvaluationDays", e.getEvaluationDays());
        result.put("rssFeedUrls", config.getContext().getRssFeedUrls());
        return result;
    }

    /**
     * Actualiza las claves indicadas en BD (upsert), re-aplica al config y devuelve el
     * payload completo. Las claves no reconocidas se ignoran.
     */
    public synchronized Map<String, Object> updateSettings(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return getSettings();
        }
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (!isKnownKey(key)) {
                log.info("Ignoring unknown automation setting '{}'", key);
                continue;
            }
            String json = serialize(entry.getValue());
            jdbcTemplate.update(
                """
                INSERT INTO automation_settings (key, value, description, updated_at)
                VALUES (?, ?::jsonb, NULL, now())
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """,
                key, json
            );
        }
        applyToConfig();
        return getSettings();
    }

    private List<Map.Entry<String, JsonNode>> readAll() {
        try {
            var rows = jdbcTemplate.queryForList("SELECT key, value FROM " + TABLE);
            List<Map.Entry<String, JsonNode>> out = new ArrayList<>();
            for (var row : rows) {
                String key = (String) row.get("key");
                String value = String.valueOf(row.get("value"));
                JsonNode node = objectMapper.readTree(value);
                out.add(Map.entry(key, node));
            }
            return out;
        } catch (Exception e) {
            log.warn("Could not read automation_settings: {}", e.getMessage());
            return List.of();
        }
    }

    private void applyValue(String key, JsonNode value) {
        switch (key) {
            // Actividad (scheduling ponderado)
            case "activityWeighted" -> config.getActivity().setWeighted(value.asBoolean());
            // Engagement
            case "engagementEnabled" -> config.getEngagement().setEnabled(value.asBoolean());
            case "engagementTopExamples" -> config.getEngagement().setTopExamples(value.asInt());
            case "engagementEvaluationDays" -> config.getEngagement().setEvaluationDays(value.asInt());
            case "engagementVotesWeight" -> config.getEngagement().setVotesWeight(value.asInt());
            case "engagementCommentsWeight" -> config.getEngagement().setCommentsWeight(value.asInt());
            case "engagementReactionsWeight" -> config.getEngagement().setReactionsWeight(value.asInt());
            case "engagementSharesWeight" -> config.getEngagement().setSharesWeight(value.asInt());
            case "engagementSavesWeight" -> config.getEngagement().setSavesWeight(value.asInt());
            case "engagementViewsWeight" -> config.getEngagement().setViewsWeight(value.asInt());
            case "rssFeedUrls" -> config.getContext().setRssFeedUrls(jsonToStringList(value));
            case "enabled" -> config.setEnabled(value.asBoolean());
            case "dryRun" -> config.setDryRun(value.asBoolean());
            case "runHour" -> config.setRunHour(value.asInt());
            case "dailyCasesMin" -> config.setDailyCasesMin(value.asInt());
            case "dailyCasesMax" -> config.setDailyCasesMax(value.asInt());
            case "usersPerCaseMin" -> config.setUsersPerCaseMin(value.asInt());
            case "usersPerCaseMax" -> config.setUsersPerCaseMax(value.asInt());
            case "maxInteractionsPerUserPerCaseMin" -> config.setMaxInteractionsPerUserPerCaseMin(value.asInt());
            case "maxInteractionsPerUserPerCaseMax" -> config.setMaxInteractionsPerUserPerCaseMax(value.asInt());
            case "intensityMin" -> config.setIntensityMin(value.asInt());
            case "intensityMax" -> config.setIntensityMax(value.asInt());
            case "schedulingIntervalMin" -> config.setSchedulingIntervalMin(value.asInt());
            case "schedulingIntervalMax" -> config.setSchedulingIntervalMax(value.asInt());
            case "schedulingWindowHours" -> config.setSchedulingWindowHours(value.asInt());
            case "dailyPoolSize" -> config.setDailyPoolSize(value.asInt());
            default -> log.info("Automation setting '{}' has no mapping; skipped", key);
        }
    }

    private List<String> jsonToStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual()) {
                    out.add(n.asText());
                }
            });
        }
        return out;
    }

    private boolean isKnownKey(String key) {
        return BOOL_KEYS.contains(key) || INT_KEYS.contains(key) || STRING_ARR_KEYS.contains(key);
    }

    private String serialize(Object value) {
        try {
            if (value instanceof String s && isJsonShape(s)) {
                // Ya viene como JSON (p.ej. array de urls)
                return s;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor inválido para key: " + value);
        }
    }

    private boolean isJsonShape(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }
}
