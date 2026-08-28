package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AiError;
import com.etribunal.ai.automation.domain.AiErrorCode;
import com.etribunal.ai.automation.domain.dtos.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock AI provider for local development and testing.
 * Returns deterministic mock responses without calling external AI services.
 */
@Component
@ConditionalOnProperty(prefix = "etribunal.automation.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAIProvider implements AIProvider {

    private final AutomationConfig automationConfig;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    public MockAIProvider(AutomationConfig automationConfig) {
        this.automationConfig = automationConfig;
    }

    @Override
    public Mono<GeneratedCase> generateCase(GenerateCaseInput input) {
        int callNum = callCounter.incrementAndGet();
        String title = "Caso mock #" + callNum + ": " + input.variationSeed();
        String sideAContent = "Contenido del lado A para el caso: " + input.variationSeed() + ". Intensidad: " + input.intensity();
        String sideBContent = "Contenido del lado B para el caso: " + input.variationSeed() + ". Intensidad: " + input.intensity();
        String sideASubtitle = "Subtítulo A mock";
        String sideBSubtitle = "Subtítulo B mock";
        String bothWrongSubtitle = "Ambos equivocados mock";
        String category = input.recentTopics().isEmpty() ? "General" : input.recentTopics().get(0);
        String caseType = "classic";

        GeneratedCase generated = new GeneratedCase(
                title,
                "Descripción mock para " + title,
                sideAContent,
                sideBContent,
                category,
                caseType,
                sideASubtitle,
                sideBSubtitle,
                bothWrongSubtitle,
                java.util.Map.of()
        );

        return Mono.just(generated);
    }

    @Override
    public Mono<InteractionPlan> generateInteractionPlan(GenerateInteractionPlanInput input) {
        List<PlannedInteraction> interactions = new ArrayList<>();
        int interactionCount = input.interactionCount();

        for (int i = 0; i < interactionCount; i++) {
            String userId = "user-" + (i % 3 + 1);
            com.etribunal.ai.automation.domain.AutomationInteractionType type;
            if (i % 3 == 0) type = com.etribunal.ai.automation.domain.AutomationInteractionType.COMMENT;
            else if (i % 3 == 1) type = com.etribunal.ai.automation.domain.AutomationInteractionType.REPLY;
            else type = com.etribunal.ai.automation.domain.AutomationInteractionType.REACTION;

            String content = "Interacción mock #" + (i + 1) + " para el caso: " + input.title();

            interactions.add(new PlannedInteraction(
                    com.etribunal.ai.automation.domain.AutomationInteractionType.COMMENT,
                    "neutral",
                    50,
                    "Interacción mock #" + (i + 1) + " para el caso: " + input.title(),
                    "",
                    "",
                    i + 1
            ));
        }

        InteractionPlan plan = new InteractionPlan(interactions);
        return Mono.just(plan);
    }

    @Override
    public Mono<GeneratedComment> generateComment(GenerateCommentInput input) {
        String content = "Comentario mock sobre: " + input.caseTitle() + " - " + input.stance();
        GeneratedComment comment = new GeneratedComment(content);
        return Mono.just(comment);
    }

    @Override
    public Mono<GeneratedReply> generateReply(GenerateReplyInput input) {
        String content = "Respuesta mock a: " + input.parentCommentContent() + " - " + input.stance();
        GeneratedReply reply = new GeneratedReply(content);
        return Mono.just(reply);
    }
}