package com.etribunal.ai.automation.infrastructure.analytics;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Perfil de actividad real por hora (0-23) para concentrar el scheduling de interacciones
 * bot en horas pico de usuarios.
 *
 * Fases (modo gradual):
 * - BOOTSTRAP: sin datos → distribución uniforme.
 * - TRANSITION: pocos datos → mezcla gradual entre uniforme y real.
 * - STABLE: datos suficientes → distribución por actividad real.
 *
 * Los pesos se normalizan para que sumen 1.0. Best-effort: si la BD no responde, se
 * mantiene la caché anterior (o uniforme al arrancar).
 *
 * <p>La tabla {@code users} vive en la BD de identity; {@code interaction_logs} y
 * {@code activity_profile} en core. Se consulta identity solo para obtener los ids de
 * bots y se filtra core con {@code NOT IN} (no hay JOIN cross-DB).
 */
@Service
public class ActivityProfileService {

    private static final Logger log = LoggerFactory.getLogger(ActivityProfileService.class);

    public enum ProfilePhase { BOOTSTRAP, TRANSITION, STABLE }

    private static final int HOURS = 24;

    private static final String COUNT_SQL_ALL =
            "SELECT count(*) FROM interaction_logs il WHERE il.created_at >= now() - make_interval(days => ?)";
    private static final String COUNT_SQL_ALL_EXCLUDING_BOTS =
            "SELECT count(*) FROM interaction_logs il WHERE il.created_at >= now() - make_interval(days => ?)"
            + " AND NOT (il.user_id = ANY(?))";
    private static final String HOURLY_SQL_ALL =
            "SELECT EXTRACT(HOUR FROM il.created_at)::int AS hour, count(*) AS cnt FROM interaction_logs il"
            + " WHERE il.created_at >= now() - make_interval(days => ?) GROUP BY 1";
    private static final String HOURLY_SQL_ALL_EXCLUDING_BOTS =
            "SELECT EXTRACT(HOUR FROM il.created_at)::int AS hour, count(*) AS cnt FROM interaction_logs il"
            + " WHERE il.created_at >= now() - make_interval(days => ?)"
            + " AND NOT (il.user_id = ANY(?)) GROUP BY 1";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate identityJdbcTemplate;
    private final AutomationConfig config;

    private final AtomicReference<double[]> weights = new AtomicReference<>(uniform());
    private final AtomicReference<ProfilePhase> phase = new AtomicReference<>(ProfilePhase.BOOTSTRAP);
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    public ActivityProfileService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("identityJdbcTemplate") JdbcTemplate identityJdbcTemplate,
            AutomationConfig config
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.identityJdbcTemplate = identityJdbcTemplate;
        this.config = config;
    }

    public ProfilePhase phase() {
        return phase.get();
    }

    public double weightForHour(int hourOfDay) {
        int h = Math.floorMod(hourOfDay, HOURS);
        return weights.get()[h];
    }

    public Map<String, Object> metrics() {
        return metrics;
    }

    /** Vuelve a calcular el perfil desde interaction_logs (excluye bots) y lo persiste. */
    public synchronized void refresh() {
        try {
            AutomationConfig.ActivityConfig a = config.getActivity();
            List<String> botIds = safeBotIds();
            int realizedTotal = countInteractionsSince(a.getLookbackDays(), botIds);
            Map<Integer, Integer> hourly = hourlyInteractionsSince(a.getLookbackDays(), botIds);

            double[] real = new double[HOURS];
            double realTotal = 0;
            for (Map.Entry<Integer, Integer> e : hourly.entrySet()) {
                real[e.getKey()] = e.getValue();
                realTotal += e.getValue();
            }

            double[] result;
            ProfilePhase p;
            if (realTotal <= 0) {
                result = uniform();
                p = ProfilePhase.BOOTSTRAP;
            } else {
                for (int i = 0; i < HOURS; i++) {
                    real[i] /= realTotal;
                }
                int d = Math.max(0, a.getMinStableSamples() - a.getMinTransitionSamples());
                double alpha;
                if (realizedTotal >= a.getMinStableSamples()) {
                    alpha = 1.0;
                    p = ProfilePhase.STABLE;
                } else if (realizedTotal > a.getMinTransitionSamples()) {
                    double fill;
                    if (d == 0) {
                        fill = 1.0;
                    } else {
                        fill = (double) (realizedTotal - a.getMinTransitionSamples()) / d;
                    }
                    alpha = 0.5 + 0.5 * fill;
                    alpha = Math.clamp(alpha, 0.0, 1.0);
                    p = ProfilePhase.TRANSITION;
                } else {
                    alpha = 0.0;
                    p = ProfilePhase.BOOTSTRAP;
                }
                result = new double[HOURS];
                for (int i = 0; i < HOURS; i++) {
                    result[i] = (1.0 - alpha) * (1.0 / HOURS) + alpha * real[i];
                }
                normalize(result);
            }

            persist(result);
            weights.set(result);
            phase.set(p);
            metrics.put("phase", p.name());
            metrics.put("samples", realizedTotal);
            metrics.put("lastRefresh", java.time.Instant.now().toString());
            log.info("Activity profile refreshed: phase={}, samples={}", p, realizedTotal);
        } catch (Exception e) {
            log.warn("Could not refresh activity profile: {}", e.getMessage());
        }
    }

    private int countInteractionsSince(int lookbackDays, List<String> botIds) {
        Integer count;
        if (botIds.isEmpty()) {
            count = jdbcTemplate.queryForObject(COUNT_SQL_ALL, Integer.class, lookbackDays);
        } else {
            count = jdbcTemplate.query(
                    COUNT_SQL_ALL_EXCLUDING_BOTS,
                    botFilterSetter(lookbackDays, botIds),
                    rs -> rs.next() ? rs.getInt(1) : 0
            );
        }
        return count == null ? 0 : count;
    }

    private Map<Integer, Integer> hourlyInteractionsSince(int lookbackDays, List<String> botIds) {
        List<Map.Entry<Integer, Integer>> rows;
        if (botIds.isEmpty()) {
            rows = jdbcTemplate.query(
                    HOURLY_SQL_ALL,
                    (rs, rowNum) -> Map.entry(rs.getInt("hour"), rs.getInt("cnt")),
                    lookbackDays
            );
        } else {
            rows = jdbcTemplate.query(
                    HOURLY_SQL_ALL_EXCLUDING_BOTS,
                    botFilterSetter(lookbackDays, botIds),
                    rs -> {
                        List<Map.Entry<Integer, Integer>> entries = new java.util.ArrayList<>();
                        while (rs.next()) {
                            entries.add(Map.entry(rs.getInt("hour"), rs.getInt("cnt")));
                        }
                        return entries;
                    }
            );
        }
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private PreparedStatementSetter botFilterSetter(int lookbackDays, List<String> botIds) {
        return ps -> {
            ps.setInt(1, lookbackDays);
            UUID[] ids = botIds.stream().map(UUID::fromString).toArray(UUID[]::new);
            ps.setArray(2, ps.getConnection().createArrayOf("uuid", ids));
        };
    }

    private void persist(double[] hourlyWeights) {
        try {
            jdbcTemplate.update("DELETE FROM activity_profile");
            for (int h = 0; h < HOURS; h++) {
                jdbcTemplate.update(
                    "INSERT INTO activity_profile (hour_of_day, weight, updated_at) VALUES (?, ?, now())",
                    h, Math.round(hourlyWeights[h] * 10000) / 10000.0
                );
            }
        } catch (Exception e) {
            log.warn("Could not persist activity profile: {}", e.getMessage());
        }
    }

    private static double[] uniform() {
        double[] u = new double[HOURS];
        java.util.Arrays.fill(u, 1.0 / HOURS);
        return u;
    }

    private static void normalize(double[] arr) {
        double sum = 0;
        for (double v : arr) {
            sum += v;
        }
        if (sum <= 0) {
            java.util.Arrays.fill(arr, 1.0 / HOURS);
            return;
        }
        for (int i = 0; i < arr.length; i++) {
            arr[i] /= sum;
        }
    }

    /** Devuelve un array-copia de los pesos actuales (suman 1.0). */
    public double[] currentWeights() {
        return weights.get().clone();
    }

    /** IDs de usuarios bot desde identity (best-effort: lista vacía si no responde). */
    private List<String> safeBotIds() {
        try {
            return identityJdbcTemplate.queryForList(
                "SELECT id::text FROM users WHERE is_bot = true AND deleted_at IS NULL",
                String.class
            );
        } catch (Exception e) {
            log.warn("Could not read bot ids from identity: {}", e.getMessage());
            return List.of();
        }
    }
}
