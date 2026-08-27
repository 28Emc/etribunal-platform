package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.domain.dtos.*;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class CaseGenerator {

    private static final Logger log = LoggerFactory.getLogger(CaseGenerator.class);
    private static final int MAX_DUPLICATE_ATTEMPTS = 3;
    private static final int MODERATION_POLL_ATTEMPTS = 14;
    private static final long MODERATION_POLL_DELAY_MS = 150;

    private final AIProvider aiProvider;
    private final AutomationConfig config;
    private final AutomationCaseRepository caseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserSelector userSelector;

    public CaseGenerator(
            AIProvider aiProvider,
            AutomationConfig config,
            AutomationCaseRepository caseRepository,
            JdbcTemplate jdbcTemplate,
            UserSelector userSelector
    ) {
        this.aiProvider = aiProvider;
        this.config = config;
        this.caseRepository = caseRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.userSelector = userSelector;
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
            GenerateCaseInput input = new GenerateCaseInput(variationSeed, recentTopics, intensity, language);
            return aiProvider.generateCase(input)
                    .map(generated -> {
                        log.info("[DRY-RUN] Case planned: {}", generated.title());
                        return new CaseResult("dry-run-" + UUID.randomUUID(), AutomationCaseStatus.PLANNED, authorId, null, generated);
                    });
        }

        // Real generation with anti-duplicate
        return generateWithDedup(runId, index, variationSeed, recentTopics, intensity, language, authorId, pool, dryRun);
    }

    private Mono<CaseResult> generateWithDedup(
            UUID runId, int index, String variationSeed,
            List<String> recentTopics, int intensity, String language,
            String authorId, List<UserSelector.BotUser> pool, boolean dryRun
    ) {
        GenerateCaseInput input = new GenerateCaseInput(variationSeed, recentTopics, intensity, language);

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

                    AutomationCaseEntity entity = new AutomationCaseEntity();
                    entity.setRun(jdbcTemplate.queryForObject(
                        "SELECT * FROM automation_runs WHERE id = ?", new Object[]{runId}, (rs, rowNum) -> null
                    ));
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
        String caseId = UUID.randomUUID().toString();
        String caseType = generated.caseType() != null ? generated.caseType() : "classic";

        jdbcTemplate.update(
            """
            INSERT INTO cases (id, title, description, side_a_content, side_b_content, category,
                case_type, side_a_subtitle, side_b_subtitle, both_wrong_subtitle,
                author_id, status, moderation_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING', 'PENDING', ?, ?)
            """,
            caseId, generated.title(), generated.description(),
            generated.sideAContent(), generated.sideBContent(),
            generated.category(), caseType,
            generated.sideASubtitle(), generated.sideBSubtitle(), generated.bothWrongSubtitle(),
            authorId, Instant.now(), Instant.now()
        );
        return caseId;
    }

    private void respondAsSideB(String caseId, String sideBUserId, String sideBContent) {
        jdbcTemplate.update(
            """
            UPDATE cases SET side_b_user_id = ?, side_b_content = ?,
                status = 'PUBLIC', updated_at = ?
            WHERE id = ?
            """,
            sideBUserId, sideBContent, Instant.now(), caseId
        );
    }

    private void pollModeration(String caseId) {
        for (int i = 0; i < MODERATION_POLL_ATTEMPTS; i++) {
            try {
                String status = jdbcTemplate.queryForObject(
                    "SELECT moderation_status FROM cases WHERE id = ?",
                    new Object[]{caseId},
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
        return pool.get(new Random().nextInt(pool.size())).id();
    }

    private String pickSideBUser(List<UserSelector.BotUser> pool, String authorId) {
        List<UserSelector.BotUser> candidates = pool.stream()
                .filter(u -> !u.id().equals(authorId))
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.get(new Random().nextInt(candidates.size())).id();
    }

    private String computeHash(String title, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
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