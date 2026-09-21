package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.domain.dtos.*;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.context.LiveContextService;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class CaseGenerator {

    private static final Logger log = LoggerFactory.getLogger(CaseGenerator.class);
    private static final int MODERATION_POLL_ATTEMPTS = 14;
    private static final long MODERATION_POLL_DELAY_MS = 150;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AIProvider aiProvider;
    private final AutomationConfig config;
    private final AutomationCaseRepository caseRepository;
    private final AutomationRunRepository runRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AutomationEventPublisher eventPublisher;
    private final EngagementService engagementService;
    private final LiveContextService liveContextService;

    public CaseGenerator(
            AIProvider aiProvider,
            AutomationConfig config,
            AutomationCaseRepository caseRepository,
            AutomationRunRepository runRepository,
            JdbcTemplate jdbcTemplate,
            AutomationEventPublisher eventPublisher,
            EngagementService engagementService,
            LiveContextService liveContextService
    ) {
        this.aiProvider = aiProvider;
        this.config = config;
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.engagementService = engagementService;
        this.liveContextService = liveContextService;
    }

    public record CaseResult(
        String caseId,
        AutomationCaseStatus status,
        String authorId,
        String sideBUserId,
        GeneratedCase generated
    ) {}

    @Transactional
    public Mono<CaseResult> generateCase(
            UUID runId,
            int index,
            List<String> recentTopics,
            List<UserSelector.BotUser> pool,
            boolean dryRun
    ) {
        String authorId = pickRandomUserId(pool);
        if (authorId == null) {
            return Mono.just(new CaseResult(null, AutomationCaseStatus.REJECTED, null, null, null));
        }

        String variationSeed = UUID.randomUUID().toString().substring(0, 8);
        int intensity = config.pickIntensity();
        String language = config.getLanguage();

        if (dryRun) {
            GenerateCaseInput input = new GenerateCaseInput(variationSeed, recentTopics, intensity, language, loadSuccessExamples(), liveContextService.buildContext());
            return aiProvider.generateCase(input)
                    .map(generated -> {
                        log.info("[DRY-RUN] Case planned: {}", generated.title());
                        return new CaseResult("dry-run-" + UUID.randomUUID(), AutomationCaseStatus.PLANNED, authorId, null, generated);
                    });
        }

        // Real generation with anti-duplicate
        return generateWithDedup(runId, index, variationSeed, recentTopics, intensity, language, authorId, pool, dryRun);
    }

    private List<String> loadSuccessExamples() {
        if (!config.getEngagement().isEnabled()) {
            return List.of();
        }
        try {
            return engagementService.findTopPerformingCases(config.getEngagement().getTopExamples())
                    .stream()
                    .map(p -> p.title() + " (score " + p.engagementScore() + ")")
                    .toList();
        } catch (Exception e) {
            log.warn("Could not load success examples: {}", e.getMessage());
            return List.of();
        }
    }

    private Mono<CaseResult> generateWithDedup(
            UUID runId, int index, String variationSeed,
            List<String> recentTopics, int intensity, String language,
            String authorId, List<UserSelector.BotUser> pool, boolean dryRun
    ) {
        GenerateCaseInput input = new GenerateCaseInput(variationSeed, recentTopics, intensity, language, loadSuccessExamples(), liveContextService.buildContext());

        return aiProvider.generateCase(input)
                .flatMap(generated -> {
                    String hash = computeHash(generated.title(), generated.sideAContent());
                    Optional<AutomationCaseEntity> duplicate = caseRepository.findByCaseId("dup-" + hash.substring(0, 16));
                    if (duplicate.isPresent()) {
                        log.warn("Duplicate case detected, retrying (hash={})", hash.substring(0, 16));
                        return Mono.empty();
                    }

                    String caseId = persistCase(generated, authorId);
                    pollModeration(caseId);

                    String sideBUserId = null;
                    if ("vote".equalsIgnoreCase(generated.caseType())) {
                        sideBUserId = pickSideBUser(pool, authorId);
                        if (sideBUserId != null) {
                            respondAsSideB(caseId, sideBUserId, generated.sideBContent());
                        }
                    }

                    eventPublisher.publishCaseCreated(
                            UUID.fromString(caseId),
                            UUID.fromString(authorId),
                            sideBUserId != null ? UUID.fromString(sideBUserId) : null,
                            generated.caseType()
                    );

                    AutomationCaseEntity entity = new AutomationCaseEntity();
                    entity.setRun(runRepository.findById(runId).orElseThrow());
                    entity.setCaseId(caseId);
                    entity.setStatus(AutomationCaseStatus.CREATED);
                    entity.setMetadata(Map.of("title", generated.title(), "hash", hash, "index", index));
                    caseRepository.save(entity);

                    return Mono.just(new CaseResult(caseId, AutomationCaseStatus.CREATED, authorId, sideBUserId, generated));
                })
                .onErrorResume(e -> {
                    log.error("Case generation failed: {}", e.getMessage());
                    return Mono.just(new CaseResult(null, AutomationCaseStatus.FAILED, authorId, null, null));
                });
    }

    private String persistCase(GeneratedCase generated, String authorId) {
        UUID caseId = UUID.randomUUID();
        UUID authorUuid = UUID.fromString(authorId);

        String rawCaseType = generated.caseType();
        String caseType = normalizeCaseType(rawCaseType);
        String category = normalizeCategory(generated.category());
        String language = config.getLanguage() != null ? config.getLanguage() : "es";
        boolean isVote = "vote".equals(caseType);
        String inviteToken = isVote ? UUID.randomUUID().toString() : null;

        log.info("AI case values: title='{}', caseType='{}' (raw='{}'), category='{}', sideA_len={}, sideB_len={}, authorId='{}', inviteToken={}",
                generated.title(), caseType, rawCaseType, category,
                generated.sideAContent() != null ? generated.sideAContent().length() : 0,
                generated.sideBContent() != null ? generated.sideBContent().length() : 0,
                authorId, inviteToken);

        try {
            String title = truncate(generated.title(), 100);
            jdbcTemplate.update(
                """
                INSERT INTO cases (id, title, slug, side_a_content, side_b_content, category,
                    type, side_a_subtitle, side_b_subtitle, both_wrong_subtitle,
                    content_language, is_anonymous, is_private, moderation_status,
                    side_a_user_id, side_b_user_id, invite_token, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, false, 'PENDING', ?, ?, ?, ?, ?, ?)
                """,
                caseId,
                title,
                generateSlug(title),
                truncate(generated.sideAContent(), 65535),
                truncate(generated.sideBContent(), 65535),
                category,
                caseType,
                truncate(generated.sideASubtitle(), 50),
                truncate(generated.sideBSubtitle(), 50),
                truncate(generated.bothWrongSubtitle(), 50),
                language,
                authorUuid,
                null,  // side_b_user_id (null initially for vote cases)
                inviteToken,
                isVote ? "WAITING" : "PUBLIC",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
            );
            return caseId.toString();
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            log.error("SQL ERROR persisting case: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("  Root cause: {}", e.getCause().getMessage());
                if (e.getCause().getCause() != null) {
                    log.error("  Deep cause: {}", e.getCause().getCause().getMessage());
                }
            }
            throw e;
        }
    }

    private String normalizeCaseType(String raw) {
        if (raw == null) return "classic";
        String normalized = raw.trim().toLowerCase();
        return "vote".equals(normalized) ? "vote" : "classic";
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "General";
        String trimmed = raw.trim();
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /**
     * Genera un slug SEO-friendly a partir del título. Réplica del generateSlug
     * del core-domain-service para que las deep links /cases/:username/:slug
     * resuelvan correctamente.
     */
    private static String generateSlug(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return slug.length() > 100 ? slug.substring(0, 100) : slug;
    }

    private void respondAsSideB(String caseId, String sideBUserId, String sideBContent) {
        UUID caseUuid = UUID.fromString(caseId);
        UUID sideBUuid = UUID.fromString(sideBUserId);

        // El caso ya trae su invite_token (asignado al insertar en WAITING). No se genera
        // uno nuevo: el UPDATE para responder Side B debe usar el token existente de la fila.
        int retries = 3;
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                int updated = jdbcTemplate.update(
                    """
                    UPDATE cases SET side_b_user_id = ?, side_b_content = ?,
                        status = 'PUBLIC', invite_token = null, updated_at = ?
                    WHERE id = ? AND status = 'WAITING' AND side_b_user_id IS NULL
                    """,
                    sideBUuid, sideBContent, Timestamp.from(Instant.now()),
                    caseUuid
                );

                if (updated > 0) {
                    log.info("Side B response successful for case {}", caseId);
                    return;
                } else {
                    // Check why it didn't update
                    String status = jdbcTemplate.queryForObject(
                        "SELECT status FROM cases WHERE id = ?", new Object[]{caseUuid}, String.class
                    );
                    log.warn("Side B response no rows updated (attempt {}/{}): status={}",
                            attempt, retries, status);
                }
            } catch (Exception e) {
                log.warn("Side B response attempt {}/{} failed: {}", attempt, retries, e.getMessage());
            }
            // Brief backoff
            try { Thread.sleep(100L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }

        // Fallback: convert to classic (type=classic, status=PUBLIC) so case is visible in feed
        log.warn("Side B response failed after {} retries for case {}, converting to classic", retries, caseId);
        fallbackToClassic(caseId);
    }

    private void fallbackToClassic(String caseId) {
        UUID caseUuid = UUID.fromString(caseId);
        try {
            int updated = jdbcTemplate.update(
                """
                UPDATE cases SET type = 'classic', status = 'PUBLIC',
                    side_b_content = COALESCE(side_b_content, 'Sin respuesta de Side B'),
                    side_b_user_id = COALESCE(side_b_user_id, side_a_user_id),
                    updated_at = ?
                WHERE id = ? AND type = 'vote' AND status = 'WAITING'
                """,
                Timestamp.from(Instant.now()), caseUuid
            );
            if (updated > 0) {
                log.info("Case {} converted to classic (PUBLIC)", caseId);
            } else {
                log.warn("Case {} could not be converted to classic (maybe already processed)", caseId);
            }
        } catch (Exception e) {
            log.error("Fallback to classic failed for case {}: {}", caseId, e.getMessage());
        }
    }

    private void pollModeration(String caseId) {
        UUID caseUuid = UUID.fromString(caseId);
        for (int i = 0; i < MODERATION_POLL_ATTEMPTS; i++) {
            try {
                String status = jdbcTemplate.queryForObject(
                    "SELECT moderation_status FROM cases WHERE id = ?",
                    new Object[]{caseUuid},
                    String.class
                );
                if (status != null && !("PENDING".equals(status))) {
                    return;
                }
                Thread.sleep(MODERATION_POLL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String pickRandomUserId(List<UserSelector.BotUser> pool) {
        if (pool.isEmpty()) return null;
        return pool.get(RANDOM.nextInt(pool.size())).id();
    }

    private String pickSideBUser(List<UserSelector.BotUser> pool, String authorId) {
        List<UserSelector.BotUser> candidates = pool.stream()
                .filter(u -> !u.id().equals(authorId))
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.get(RANDOM.nextInt(candidates.size())).id();
    }

    private String computeHash(String title, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (title + content).toLowerCase().trim();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}