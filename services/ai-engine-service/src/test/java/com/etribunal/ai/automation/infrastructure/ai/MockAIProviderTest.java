package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.domain.dtos.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

class MockAIProviderTest {

    private final MockAIProvider provider = new MockAIProvider();

    @Test
    void generateCase_returnsDeterministicCase() {
        GenerateCaseInput input = new GenerateCaseInput(
                "seed-xyz", List.of("politics"), 50, "es", List.of());

        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result).isNotNull();
        assertThat(result.title()).contains("Caso mock #1");
        assertThat(result.title()).contains("seed-xyz");
        assertThat(result.caseType()).isEqualTo("classic");
        assertThat(result.category()).isEqualTo("politics");
        assertThat(result.sideAContent()).contains("seed-xyz");
        assertThat(result.sideBContent()).contains("Intensidad: 50");
        assertThat(result.metadata()).isNotNull();
    }

    @Test
    void generateCase_emptyTopics_defaultsCategory() {
        GenerateCaseInput input = new GenerateCaseInput(
                "s", List.of(), 30, "es", List.of());

        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result.category()).isEqualTo("General");
    }

    @Test
    void generateCase_incrementsCallCounter() {
        GenerateCaseInput input = new GenerateCaseInput("s", List.of(), 50, "es", List.of());

        GeneratedCase first = provider.generateCase(input).block();
        GeneratedCase second = provider.generateCase(input).block();

        assertThat(first.title()).contains("#1");
        assertThat(second.title()).contains("#2");
    }

    @Test
    void generateInteractionPlan_returnsRequestedCount() {
        GenerateInteractionPlanInput input = new GenerateInteractionPlanInput(
                "c1", "Title", "A", "B", "Other", 5, 50, "es", 10, 2);

        InteractionPlan plan = provider.generateInteractionPlan(input).block();

        assertThat(plan).isNotNull();
        assertThat(plan.interactions()).hasSize(5);
        assertThat(plan.interactions().get(0).type())
                .isEqualTo(com.etribunal.ai.automation.domain.AutomationInteractionType.COMMENT);
        assertThat(plan.interactions().get(0).content()).contains("Title");
        assertThat(plan.interactions().get(0).replyToIndex()).isEqualTo(1);
    }

    @Test
    void generateInteractionPlan_zeroCount_returnsEmpty() {
        GenerateInteractionPlanInput input = new GenerateInteractionPlanInput(
                "c1", "T", "A", "B", "Other", 0, 50, "es", 10, 2);

        InteractionPlan plan = provider.generateInteractionPlan(input).block();

        assertThat(plan.interactions()).isEmpty();
    }

    @Test
    void generateComment_returnsContent() {
        GenerateCommentInput input = new GenerateCommentInput(
                "c1", "Case Title", "A", "B", "pro-A", 50, "es");

        GeneratedComment comment = provider.generateComment(input).block();

        assertThat(comment.content()).contains("Case Title");
        assertThat(comment.content()).contains("pro-A");
    }

    @Test
    void generateReply_returnsContent() {
        GenerateReplyInput input = new GenerateReplyInput(
                "c1", "Title", "parent text", "contra", 60, "es");

        GeneratedReply reply = provider.generateReply(input).block();

        assertThat(reply.content()).contains("parent text");
        assertThat(reply.content()).contains("contra");
    }

    @Test
    void allMethods_returnMono() {
        assertThat(provider.generateCase(new GenerateCaseInput("s", List.of(), 1, "es", List.of())))
                .isInstanceOf(Mono.class);
        assertThat(provider.generateComment(new GenerateCommentInput("c", "t", "a", "b", "s", 1, "es")))
                .isInstanceOf(Mono.class);
    }
}
