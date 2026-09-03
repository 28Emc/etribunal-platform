package com.etribunal.ai.automation.infrastructure.analytics;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Feedback loop 2.0: mide la performance de los casos generados por IA y expone los
 * "ejemplos de éxito" para afinar los prompts de generación.
 *
 * Los conteos se obtienen de las tablas compartidas (cases + subconsultas de
 * reactions/saved_cases). El score se normaliza a 0-100 con pesos configurables en
 * {@link AutomationConfig.EngagementConfig}. Best-effort: si no hay datos, devuelve vacío
 * y la generación sigue "a ciegas" (modo gradual).
 */
@Service
public class EngagementService {

    private static final Logger log = LoggerFactory.getLogger(EngagementService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AutomationConfig config;

    public EngagementService(JdbcTemplate jdbcTemplate, AutomationConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
    }

    public record CasePerformance(
            String caseId,
            String title,
            int engagementScore,
            int votes,
            int comments,
            int reactions,
            int shares,
            int saves,
            int views,
            LocalDate evaluationDate
    ) {}

    /** Calcula el score ponderado 0-100 a partir de los conteos. Expuesto para tests. */
    public int calculateScore(int votes, int comments, int reactions, int shares, int saves, int views) {
        AutomationConfig.EngagementConfig e = config.getEngagement();
        double weighted = votes * e.getVotesWeight()
                + comments * e.getCommentsWeight()
                + reactions * e.getReactionsWeight()
                + shares * e.getSharesWeight()
                + saves * e.getSavesWeight()
                + views * e.getViewsWeight();
        // Normalización logarítmica para acotar a 0-100 sin que casos virales dominen
        double score = 100.0 * (1.0 - Math.exp(-weighted / 60.0));
        return (int) Math.round(score);
    }

    /** Devuelve los casos generados por IA de mejor score, recientes. Vacío si no hay datos. */
    public List<CasePerformance> findTopPerformingCases(int topN) {
        int limit = Math.max(1, Math.min(50, topN));
        try {
            return jdbcTemplate.query(
                """
                SELECT cp.case_id, c.title, cp.engagement_score, cp.votes, cp.comments,
                       cp.reactions, cp.shares, cp.saves, cp.views, cp.evaluation_date
                FROM case_performance cp
                JOIN cases c ON c.id = cp.case_id
                WHERE c.deleted_at IS NULL
                ORDER BY cp.engagement_score DESC, cp.updated_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new CasePerformance(
                        rs.getString("case_id"),
                        rs.getString("title"),
                        rs.getInt("engagement_score"),
                        rs.getInt("votes"),
                        rs.getInt("comments"),
                        rs.getInt("reactions"),
                        rs.getInt("shares"),
                        rs.getInt("saves"),
                        rs.getInt("views"),
                        rs.getObject("evaluation_date", LocalDate.class)
                ),
                limit
            );
        } catch (Exception e) {
            log.warn("Could not read top performing cases: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Evalúa los casos generados por IA de los últimos N días: computa conteos, scores y
     * hace upsert en case_performance. Devuelve cuántos casos se evaluaron.
     */
    public int evaluateRecentCases(int days) {
        try {
            java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofDays(Math.max(1, days)));
            List<CasePerformance> performances = jdbcTemplate.query(
                """
                SELECT
                    c.id::text AS case_id,
                    c.title,
                    c.total_votes AS votes,
                    c.total_comments AS comments,
                    (SELECT count(*) FROM reactions r WHERE r.case_id = c.id) AS reactions,
                    c.total_shares AS shares,
                    (SELECT count(*) FROM saved_cases sc WHERE sc.case_id = c.id) AS saves,
                    c.total_views AS views
                FROM automation_cases ac
                JOIN cases c ON c.id::text = ac.case_id
                WHERE c.created_at >= ?
                  AND c.deleted_at IS NULL
                """,
                (rs, rowNum) -> new CasePerformance(
                        rs.getString("case_id"),
                        rs.getString("title"),
                        0,
                        rs.getInt("votes"),
                        rs.getInt("comments"),
                        rs.getInt("reactions"),
                        rs.getInt("shares"),
                        rs.getInt("saves"),
                        rs.getInt("views"),
                        LocalDate.now()
                ),
                since
            );

            for (CasePerformance perf : performances) {
                int score = calculateScore(perf.votes(), perf.comments(), perf.reactions(),
                        perf.shares(), perf.saves(), perf.views());
                jdbcTemplate.update(
                    """
                    INSERT INTO case_performance
                        (case_id, engagement_score, votes, comments, reactions, shares, saves,
                         views, evaluation_date, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, now(), now())
                    ON CONFLICT (case_id) DO UPDATE SET
                        engagement_score = EXCLUDED.engagement_score,
                        votes = EXCLUDED.votes,
                        comments = EXCLUDED.comments,
                        reactions = EXCLUDED.reactions,
                        shares = EXCLUDED.shares,
                        saves = EXCLUDED.saves,
                        views = EXCLUDED.views,
                        evaluation_date = EXCLUDED.evaluation_date,
                        updated_at = now()
                    """,
                    java.util.UUID.fromString(perf.caseId()),
                    score,
                    perf.votes(),
                    perf.comments(),
                    perf.reactions(),
                    perf.shares(),
                    perf.saves(),
                    perf.views()
                );
            }
            return performances.size();
        } catch (Exception e) {
            log.warn("Could not evaluate recent cases: {}", e.getMessage());
            return 0;
        }
    }
}
