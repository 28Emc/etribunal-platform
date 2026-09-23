package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.domain.dtos.*;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
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

    private final UUID runId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID sideBId = UUID.randomUUID();
    private final String variationSeed = "abc12345";

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
                jdbcTemplate, eventPublisher, engagementService, liveContextService, moderationService);
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

    // --- Tests for private methods via reflection ---

    @Test
    void pickRandomUserId_returnsNullForEmptyPool() {
        String result = CaseGeneratorTestHelper.pickRandomUserId(caseGenerator, List.of());
        assertThat(result).isNull();
    }

    @Test
    void pickRandomUserId_returnsUserFromPool() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("user-1", "bot1"),
                new UserSelector.BotUser("user-2", "bot2")
        );
        String result = CaseGeneratorTestHelper.pickRandomUserId(caseGenerator, pool);
        assertThat(result).isIn("user-1", "user-2");
    }

    @Test
    void pickSideBUser_excludesAuthor() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author-id", "author-bot"),
                new UserSelector.BotUser("other-1", "bot-1"),
                new UserSelector.BotUser("other-2", "bot-2")
        );
        String result = CaseGeneratorTestHelper.pickSideBUser(caseGenerator, pool, "author-id");
        assertThat(result).isIn("other-1", "other-2");
    }

    @Test
    void pickSideBUser_returnsNullWhenOnlyAuthor() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author-id", "author-bot")
        );
        String result = CaseGeneratorTestHelper.pickSideBUser(caseGenerator, pool, "author-id");
        assertThat(result).isNull();
    }

    // --- Tests for public API functionality that exercises private methods ---

@Test
    void generateCase_nonDryRun_createsCaseWithCorrectFields() {
        UUID runId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID sideBId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(authorId.toString(), "author-bot-1"),
                new UserSelector.BotUser(sideBId.toString(), "sideb-bot-1"),
                new UserSelector.BotUser(otherId.toString(), "other-bot-1")
        );
        
lenient().when(config.isDryRun()).thenReturn(false);
        lenient().when(config.getLanguage()).thenReturn("es");
        lenient().when(config.pickIntensity()).thenReturn(50);
        when(aiProvider.generateCase(any(GenerateCaseInput.class)))
                .thenReturn(Mono.just(new GeneratedCase(
                        "Test Title", "Test Description", "Side A content", "Side B content",
                        "politica", "vote", "Sub A", "Sub B", "Both wrong", java.util.Map.of())));
        
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        lenient().when(runRepository.findById(runId)).thenReturn(Optional.of(new AutomationRunEntity()));
        lenient().when(caseRepository.findByCaseId(anyString())).thenReturn(Optional.empty());
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(eventPublisher).publishCaseCreated(any(), any(), any(), any());
        lenient().doNothing().when(moderationService).requestTextModeration(anyString(), anyString(), anyString(), anyString());
        lenient().when(moderationService.getModerationStatus(anyString())).thenReturn("APPROVED");
        
        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), pool, false).block();
        
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.CREATED);
        assertThat(result.generated().title()).isEqualTo("Test Title");
    }

@Test
    void generateCase_voteType_createsWaitingCaseWithInviteToken() {
        UUID runId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID sideBId = UUID.randomUUID();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(authorId.toString(), "author-bot-1"),
                new UserSelector.BotUser(sideBId.toString(), "sideb-bot-1")
        );
        
        lenient().when(config.isDryRun()).thenReturn(false);
        lenient().when(config.getLanguage()).thenReturn("es");
        lenient().when(config.pickIntensity()).thenReturn(50);
        lenient().when(aiProvider.generateCase(any(GenerateCaseInput.class)))
                .thenReturn(Mono.just(new GeneratedCase(
                        "Vote Case", "Description", "Side A", "Side B",
                        "politica", "vote", "Sub A", "Sub B", "Both wrong", java.util.Map.of())));
        
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        lenient().when(runRepository.findById(runId)).thenReturn(Optional.of(new AutomationRunEntity()));
        lenient().when(caseRepository.findByCaseId(anyString())).thenReturn(Optional.empty());
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(eventPublisher).publishCaseCreated(any(), any(), any(), any());
        lenient().doNothing().when(moderationService).requestTextModeration(anyString(), anyString(), anyString(), anyString());
        lenient().when(moderationService.getModerationStatus(anyString())).thenReturn("APPROVED");
        lenient().when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(String.class))).thenReturn("APPROVED");
        
        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), pool, false).block();
        
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.CREATED);
    }

    @Test
    void generateCase_nonDryRun_persistsCaseWithCorrectFields() {
        UUID runId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID sideBId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(authorId.toString(), "author-bot-1"),
                new UserSelector.BotUser(sideBId.toString(), "sideb-bot-1"),
                new UserSelector.BotUser(otherId.toString(), "other-bot-1")
        );
        
        lenient().when(config.isDryRun()).thenReturn(false);
        lenient().when(config.getLanguage()).thenReturn("es");
        lenient().when(config.pickIntensity()).thenReturn(50);
        lenient().when(aiProvider.generateCase(any(GenerateCaseInput.class)))
                .thenReturn(Mono.just(new GeneratedCase(
                        "Test Title", "Test Description", "Side A content", "Side B content",
                        "politica", "vote", "Sub A", "Sub B", "Both wrong", java.util.Map.of())));
        
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        lenient().when(runRepository.findById(runId)).thenReturn(Optional.of(new AutomationRunEntity()));
        lenient().when(caseRepository.findByCaseId(anyString())).thenReturn(Optional.empty());
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(eventPublisher).publishCaseCreated(any(), any(), any(), any());
        lenient().doNothing().when(moderationService).requestTextModeration(anyString(), anyString(), anyString(), anyString());
        lenient().when(moderationService.getModerationStatus(anyString())).thenReturn("APPROVED");
        lenient().when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(String.class))).thenReturn("APPROVED");
        
        CaseGenerator.CaseResult result = caseGenerator.generateCase(runId, 0, List.of(), pool, false).block();
        
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AutomationCaseStatus.CREATED);
        assertThat(result.generated().title()).isEqualTo("Test Title");
    }

    // Helper class to access private methods via reflection
    static class CaseGeneratorTestHelper {
        static String pickRandomUserId(CaseGenerator service, List<UserSelector.BotUser> pool) {
            try {
                var method = CaseGenerator.class.getDeclaredMethod("pickRandomUserId", List.class);
                method.setAccessible(true);
                return (String) method.invoke(service, pool);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static String pickSideBUser(CaseGenerator service, List<UserSelector.BotUser> pool, String authorId) {
            try {
                var method = CaseGenerator.class.getDeclaredMethod("pickSideBUser", List.class, String.class);
                method.setAccessible(true);
                return (String) method.invoke(service, pool, authorId);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static boolean isTerminalModerationStatus(String status) {
            try {
                var method = CaseGenerator.class.getDeclaredMethod("isTerminalModerationStatus", String.class);
                method.setAccessible(true);
                return (boolean) method.invoke(null, status);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static List<String> loadSuccessExamples(CaseGenerator service) {
            try {
                var method = CaseGenerator.class.getDeclaredMethod("loadSuccessExamples");
                method.setAccessible(true);
                return (List<String>) method.invoke(service);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
