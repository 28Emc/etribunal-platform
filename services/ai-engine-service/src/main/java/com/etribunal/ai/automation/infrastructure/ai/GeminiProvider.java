package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AiError;
import com.etribunal.ai.automation.domain.AiErrorCode;
import com.etribunal.ai.automation.domain.dtos.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "etribunal.automation.ai", name = "provider", havingValue = "gemini")
public class GeminiProvider implements AIProvider {

    private final ChatClient chatClient;
    private final RateLimiter rateLimiter;
    private final OutputValidator outputValidator;
    private final AutomationConfig.AiConfig aiConfig;

    public GeminiProvider(
            ChatClient geminiChatClient,
            RateLimiter rateLimiter,
            OutputValidator outputValidator,
            AutomationConfig automationConfig
    ) {
        this.chatClient = geminiChatClient;
        this.rateLimiter = rateLimiter;
        this.outputValidator = outputValidator;
        this.aiConfig = automationConfig.getAi();
    }

    @Override
    public Mono<GeneratedCase> generateCase(GenerateCaseInput input) {
        int estimatedTokens = aiConfig.getMaxOutputTokensCase();
        return rateLimiter.acquire(estimatedTokens)
                .then(Mono.defer(() -> {
                    String systemPrompt = buildCasePrompt(input);
                    return callAndParse(systemPrompt, GeneratedCase.class, 60);
                }));
    }

    @Override
    public Mono<InteractionPlan> generateInteractionPlan(GenerateInteractionPlanInput input) {
        int estimatedTokens = aiConfig.getMaxOutputTokensPlan();
        return rateLimiter.acquire(estimatedTokens)
                .then(Mono.defer(() -> {
                    String systemPrompt = buildPlanPrompt(input);
                    return callAndParse(systemPrompt, InteractionPlan.class, 120);
                }));
    }

    @Override
    public Mono<GeneratedComment> generateComment(GenerateCommentInput input) {
        int estimatedTokens = aiConfig.getMaxOutputTokensComment();
        return rateLimiter.acquire(estimatedTokens)
                .then(Mono.defer(() -> {
                    String systemPrompt = buildCommentPrompt(input);
                    return callAndParse(systemPrompt, GeneratedComment.class, 30);
                }));
    }

    @Override
    public Mono<GeneratedReply> generateReply(GenerateReplyInput input) {
        int estimatedTokens = aiConfig.getMaxOutputTokensReply();
        return rateLimiter.acquire(estimatedTokens)
                .then(Mono.defer(() -> {
                    String systemPrompt = buildReplyPrompt(input);
                    return callAndParse(systemPrompt, GeneratedReply.class, 30);
                }));
    }

    private <T> Mono<T> callAndParse(String systemPrompt, Class<T> targetType, long timeoutSeconds) {
        return Mono.fromCallable(() -> {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .call()
                    .chatResponse();
            return extractContent(response);
        })
        .flatMap(raw -> outputValidator.validate(raw, targetType))
        .onErrorMap(e -> !(e instanceof AiError),
                e -> new AiError(AiErrorCode.PROVIDER_ERROR, e.getMessage(), true))
        .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    private String buildCasePrompt(GenerateCaseInput input) {
        String p = PromptUtils.caseGenerationPrompt(input.language(), input.intensity());
        p = p.replace("{recentTopics}", String.join(", ", input.recentTopics()));
        p = p.replace("{variationSeed}", input.variationSeed());
        return p;
    }

    private String buildPlanPrompt(GenerateInteractionPlanInput input) {
        String p = PromptUtils.interactionPlanningPrompt(input.language(), input.intensity());
        p = p.replace("{interactionCount}", String.valueOf(input.interactionCount()));
        p = p.replace("{availableUsers}", String.valueOf(input.availableUsers()));
        p = p.replace("{maxPerUser}", String.valueOf(input.maxPerUser()));
        p = p.replace("{title}", input.title());
        p = p.replace("{sideA}", input.sideAContent());
        p = p.replace("{sideB}", input.sideBContent());
        p = p.replace("{category}", input.category());
        p = p.replace("{intensity}", String.valueOf(input.intensity()));
        return p;
    }

    private String buildCommentPrompt(GenerateCommentInput input) {
        String p = PromptUtils.commentGenerationPrompt(input.language(), input.tone());
        p = p.replace("{title}", input.caseTitle());
        p = p.replace("{sideA}", input.caseSideA());
        p = p.replace("{sideB}", input.caseSideB());
        p = p.replace("{stance}", input.stance());
        p = p.replace("{intensity}", String.valueOf(input.tone()));
        return p;
    }

    private String buildReplyPrompt(GenerateReplyInput input) {
        String p = PromptUtils.replyGenerationPrompt(input.language(), input.tone());
        p = p.replace("{title}", input.caseTitle());
        p = p.replace("{parentComment}", input.parentCommentContent());
        p = p.replace("{stance}", input.stance());
        p = p.replace("{intensity}", String.valueOf(input.tone()));
        return p;
    }

    private String extractContent(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AiError(AiErrorCode.INVALID_OUTPUT, "Empty AI response", true);
        }
        return response.getResult().getOutput().getText();
    }
}