package com.etribunal.ai.automation.infrastructure.analytics;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
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
 */
@Service
public class ActivityProfileService {

    private static final Logger log = LoggerFactory.getLogger(ActivityProfileService.class);

    public enum ProfilePhase { BOOTSTRAP, TRANSITION, STABLE }

    private static final int HOURS = 24;

    private final JdbcTemplate jdbcTemplate;
    private final AutomationConfig config;

    private final AtomicReference<double[]> weights = new AtomicReference<>(uniform());
    private final AtomicReference<ProfilePhase> phase = new AtomicReference<>(ProfilePhase.BOOTSTRAP);
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    public ActivityProfileService(JdbcTemplate jdbcTemplate, AutomationConfig config) {
        this.jdbcTemplate = jdbcTemplate;
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
            int realizedTotal = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM interaction_logs il
                LEFT JOIN users u ON u.id = il.user_id
                WHERE il.created_at >= now() - make_interval(days => ?)
                  AND (u.id IS NULL OR u.is_bot = false)
                """,
                Integer.class,
                a.getLookbackDays()
            );

            Map<Integer, Integer> hourly = jdbcTemplate.query(
                """
                SELECT EXTRACT(HOUR FROM il.created_at)::int AS hour, count(*) AS cnt
                FROM interaction_logs il
                LEFT JOIN users u ON u.id = il.user_id
                WHERE il.created_at >= now() - make_interval(days => ?)
                  AND (u.id IS NULL OR u.is_bot = false)
                GROUP BY 1
                """,
                (rs, rowNum) -> Map.entry(rs.getInt("hour"), rs.getInt("cnt")),
                a.getLookbackDays()
            ).stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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
                    alpha = 0.5 + 0.5 * ((realizedTotal - a.getMinTransitionSamples()) / (double) Math.max(1, d));
                    alpha = Math.min(1.0, Math.max(0.0, alpha));
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
}
