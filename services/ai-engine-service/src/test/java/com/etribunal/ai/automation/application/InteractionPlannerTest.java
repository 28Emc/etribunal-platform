package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.ai.automation.domain.dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class InteractionPlannerTest {

    @Mock
    private AIProvider aiProvider;

    @Mock
    private AutomationConfig config;

    @InjectMocks
    private InteractionPlanner planner;

    @BeforeEach
    void setUp() {
        lenient().when(config.getLanguage()).thenReturn("es");
    }

    @Test
    void generate_validPlan_returnsMergedWithUsers() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Test comment", null, null, null),
                new PlannedInteraction(AutomationInteractionType.VOTE, null, null, null, null, "A", null),
                new PlannedInteraction(AutomationInteractionType.REACTION, null, null, null, "LIKE", null, null)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test Case", "Side A", "Side B", "politica",
                        3, 50, pool, "author", "sideb", 3)
        ).block();

        assertThat(result).isNotNull();
        assertThat(result.interactions()).hasSize(3);
        assertThat(result.interactions().get(0).type()).isEqualTo(AutomationInteractionType.COMMENT);
        assertThat(result.interactions().get(0).userId()).isIn("u1", "u2");
        assertThat(result.interactions().get(1).option()).isEqualTo("A");
        assertThat(result.interactions().get(2).reaction()).isEqualTo("LIKE");
    }

    @Test
    void generate_emptyPool_returnsEmptyResult() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author", "author-bot")
        );

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test Case", "Side A", "Side B", "politica",
                        3, 50, pool, "author", null, 3)
        ).block();

        assertThat(result).isNotNull();
        assertThat(result.interactions()).isEmpty();
    }

    @Test
    void repairPlan_reanchorsInvalidReplyToNearestComment() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        // Reply with replyToIndex >= position (invalid)
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.REPLY, null, null, "Reply content", null, null, 0)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result).isNotNull();
        // Repair should have removed the unrepairable REPLY
        assertThat(result.interactions()).isEmpty();
    }

    @Test
    void validatePlan_handlesCommentWithoutContent() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "", null, null, null)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        // Current implementation: COMMENT with empty content is not removed by repair
        // (only REPLY with invalid content/index is removed)
        assertThat(result.interactions()).hasSize(1);
        assertThat(result.interactions().get(0).content()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidPlanCases")
    void validatePlan_rejectsOrKeepsInvalidInteractions(AutomationInteractionType type, String content,
            String reaction, String option, Integer replyToIndex, int expectedSize) {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(type, null, null, content, reaction, option, replyToIndex)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).hasSize(expectedSize);
    }

    static List<Arguments> invalidPlanCases() {
        return List.of(
                Arguments.of(AutomationInteractionType.REPLY, "", null, null, 0, 0),
                Arguments.of(AutomationInteractionType.REPLY, "Reply content", null, null, 0, 0),
                Arguments.of(AutomationInteractionType.REACTION, null, "INVALID", null, null, 1),
                Arguments.of(AutomationInteractionType.VOTE, null, null, "X", null, 1)
        );
    }

    @Test
    void repairPlan_repairsReplyPointingForwardToNearestComment() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        // COMMENT at index 0, REPLY at index 1 pointing to index 1 (itself)
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Comment", null, null, null),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-B", 50, "Reply", null, null, 1)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        2, 50, pool, "author", null, 3)
        ).block();

        assertThat(result).isNotNull();
        // Should have repaired: REPLY now points to index 0 (the COMMENT)
        assertThat(result.interactions()).hasSize(2);
        assertThat(result.interactions().get(1).replyToCommentIndex()).isZero();
    }

    @Test
    void repairPlan_removesReplyWithContentNull() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.REPLY, null, null, null, null, null, 0)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).isEmpty();
    }

    @Test
    void repairPlan_removesReplyWithBlankContent() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.REPLY, null, null, "  ", null, null, 0)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).isEmpty();
    }

    @Test
    void repairPlan_repairsReplyWithValidReplyToIndex() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        // COMMENT at index 0, REPLY at index 1 pointing to index 0 (valid)
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Comment", null, null, null),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-B", 50, "Reply", null, null, 0)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        2, 50, pool, "author", null, 3)
        ).block();

        assertThat(result).isNotNull();
        // Should keep both interactions as replyToIndex is valid
        assertThat(result.interactions()).hasSize(2);
        assertThat(result.interactions().get(1).replyToCommentIndex()).isZero();
    }

    @Test
    void validatePlan_nullPlan_returnsError() throws Exception {
        List<String> errors = invokeValidatePlan(null);

        assertThat(errors).containsExactly("Plan is null");
    }

    @Test
    void validatePlan_nullInteractions_returnsError() throws Exception {
        InteractionPlan plan = new InteractionPlan(null);

        List<String> errors = invokeValidatePlan(plan);

        assertThat(errors).containsExactly("Plan is null");
    }

    @Test
    void validatePlan_missingType_addsError() throws Exception {
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(null, null, null, "content", null, null, null)
        ));

        List<String> errors = invokeValidatePlan(plan);

        assertThat(errors).containsExactly("Interaction 0: missing type");
    }

    @Test
    void generate_missingTypePlan_isMergedWithFallbackRepair() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(null, null, null, "content", null, null, null)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).hasSize(1);
    }

    @Test
    void generate_replyWithRemovedParents_reremapsToComment() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        // COMMENT(0), REPLY(1) en blanco -> eliminado, REPLY(2) apuntaba al eliminado
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Comment", null, null, null),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-B", 50, "  ", null, null, 0),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-A", 50, "Reply", null, null, 1)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        3, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).hasSize(2);
        assertThat(result.interactions().get(1).replyToCommentIndex()).isZero();
    }

    @Test
    void generate_replyWithKeptParent_preservesTargetAfterShift() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        // COMMENT(0), REPLY(1) en blanco -> eliminado, REPLY(2) apuntaba al COMMENT(0)
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Comment", null, null, null),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-B", 50, "  ", null, null, 0),
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-A", 50, "Reply", null, null, 0)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        3, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).hasSize(2);
        assertThat(result.interactions().get(1).replyToCommentIndex()).isZero();
    }

    @Test
    void generate_replyWithNullTarget_usesFallbackUser() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        // REPLY sin replyToIndex -> no reparable, fallback final lo mezcla igualmente
        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.REPLY, "pro-A", 50, "Reply", null, null, null)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        1, 50, pool, "author", null, 3)
        ).block();

        assertThat(result.interactions()).hasSize(1);
        assertThat(result.interactions().get(0).userId()).isNotNull();
    }

    @Test
    void generate_moreInteractionsThanCapacity_assignsDuplicates() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1")
        );

        InteractionPlan plan = new InteractionPlan(List.of(
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "One", null, null, null),
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Two", null, null, null),
                new PlannedInteraction(AutomationInteractionType.COMMENT, "pro-A", 50, "Three", null, null, null)
        ));

        when(aiProvider.generateInteractionPlan(any())).thenReturn(Mono.just(plan));

        InteractionPlanner.PlanResult result = planner.generate(
                new InteractionPlanner.PlanInput(
                        "case-1", "Test", "A", "B", "politica",
                        3, 50, pool, "author", null, 1)
        ).block();

        assertThat(result.interactions()).hasSize(3);
        assertThat(result.interactions().get(0).userId()).isEqualTo("u1");
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeValidatePlan(InteractionPlan plan) throws Exception {
        var method = InteractionPlanner.class.getDeclaredMethod("validatePlan", InteractionPlan.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(planner, plan);
    }
}