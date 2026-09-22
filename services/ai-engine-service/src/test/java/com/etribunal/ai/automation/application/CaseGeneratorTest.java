package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AutomationCaseStatus;
import com.etribunal.ai.automation.domain.dtos.GeneratedCase;
import com.etribunal.ai.automation.domain.dtos.GenerateCaseInput;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.context.LiveContextService;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.infrastructure.kafka.AiModerationService;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CaseGeneratorTest {

    @Mock
    private AIProvider aiProvider;

    @Mock
    private AutomationConfig config;

    @Mock
    private AutomationCaseRepository caseRepository;

    @Mock
    private AutomationRunRepository runRepository;

    @Mock
    private com.etribunal.ai.automation.repository.AutomationInteractionRepository interactionRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private AutomationEventPublisher eventPublisher;

    @Mock
    private EngagementService engagementService;

    @Mock
    private LiveContextService liveContextService;

    @Mock
    private AiModerationService moderationService;

    private CaseGenerator caseGenerator;

    @BeforeEach
    void setUp() {
        lenient().when(config.isDryRun()).thenReturn(true);
        lenient().when(config.isEnabled()).thenReturn(true);
        lenient().when(config.getLanguage()).thenReturn("es");
        lenient().when(config.pickIntensity()).thenReturn(50);
        lenient().when(config.getEngagement()).thenReturn(new AutomationConfig.EngagementConfig());
        lenient().when(config.getAi()).thenReturn(new AutomationConfig.AiConfig());

        caseGenerator = new CaseGenerator(
                aiProvider, config, caseRepository, runRepository,
                null, eventPublisher, engagementService, liveContextService, moderationService);
    }

    @Test
    void computeHash_producesConsistentHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String raw = ("Test Title" + "Test Content").toLowerCase().trim();
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        String result = sb.toString();
        assertThat(result).hasSize(40).matches("[0-9a-f]{40}");
    }

    @Test
    void computeHash_sameInputProducesSameOutput() throws Exception {
        MessageDigest digest1 = MessageDigest.getInstance("SHA-1");
        MessageDigest digest2 = MessageDigest.getInstance("SHA-1");

        String input = "Same Input";
        byte[] hash1 = digest1.digest(input.getBytes(StandardCharsets.UTF_8));
        byte[] hash2 = digest2.digest(input.getBytes(StandardCharsets.UTF_8));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeHash_differentInputProducesDifferentOutput() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash1 = digest.digest("Input A".getBytes(StandardCharsets.UTF_8));
        byte[] hash2 = digest.digest("Input B".getBytes(StandardCharsets.UTF_8));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void generateCase_dryRun_returnsPlannedResult() {
        UUID runId = UUID.randomUUID();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author-1", "author-bot-1"),
                new UserSelector.BotUser("sideb-1", "sideb-bot-1")
        );
        String variationSeed = "abc12345";
        when(aiProvider.generateCase(any(GenerateCaseInput.class)))
                .thenReturn(Mono.just(new GeneratedCase(
                        "Test Title", "Test Description", "Side A content", "Side B content",
                        "politica", "vote", "Sub A", "Sub B", "Both wrong", java.util.Map.of())));

        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), pool, true).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.PLANNED);
        assertThat(result.authorId()).isIn("author-1", "sideb-1");
        assertThat(result.caseId()).startsWith("dry-run-");
        assertThat(result.generated()).isNotNull();
        assertThat(result.generated().title()).isEqualTo("Test Title");
    }

    @Test
    void generateCase_dryRun_emptyPool_returnsRejected() {
        UUID runId = UUID.randomUUID();

        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), List.of(), true).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.REJECTED);
        assertThat(result.authorId()).isNull();
    }

    @Test
    void generateCase_nonDryRun_emptyPool_returnsRejected() {
        UUID runId = UUID.randomUUID();

        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), List.of(), false).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.REJECTED);
    }

    @Test
    void generateCase_respondsSideB_picksFromPool() {
        UUID runId = UUID.randomUUID();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author-1", "author-bot-1"),
                new UserSelector.BotUser("sideb-1", "sideb-bot-1"),
                new UserSelector.BotUser("other-1", "other-bot-1")
        );
        lenient().when(config.pickIntensity()).thenReturn(50);
        lenient().when(config.getLanguage()).thenReturn("es");
        lenient().when(config.isDryRun()).thenReturn(true);
        when(aiProvider.generateCase(any(GenerateCaseInput.class)))
                .thenReturn(Mono.just(new GeneratedCase(
                        "Test Title", "Test Description", "Side A content", "Side B content",
                        "politica", "vote", "Sub A", "Sub B", "Both wrong", java.util.Map.of())));

        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), pool, true).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.PLANNED);
    }
}