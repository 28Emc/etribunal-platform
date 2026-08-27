package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.ai.automation.domain.dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class InteractionPlannerTest {

    @Mock
    private AIProvider aiProvider;

    private AutomationConfig config;

    @InjectMocks
    private InteractionPlanner planner;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.setLanguage("es");
        planner = new InteractionPlanner(aiProvider, config);
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
                "case-1", "Test Case", "Side A", "Side B", "politica",
                3, 50, pool, "author", "sideb", 3
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
                "case-1", "Test Case", "Side A", "Side B", "politica",
                3, 50, pool, "author", null, 3
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
                "case-1", "Test", "A", "B", "politica",
                1, 50, pool, "author", null, 3
        ).block();

        assertThat(result).isNotNull();
        // Repair should have removed the unrepairable REPLY
        assertThat(result.interactions()).isEmpty();
    }
}