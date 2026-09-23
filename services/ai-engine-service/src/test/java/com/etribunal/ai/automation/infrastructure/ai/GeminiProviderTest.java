package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Mono;

import java.util.List;

class GeminiProviderTest {

    private ChatClient chatClient;
    private RateLimiter rateLimiter;
    private OutputValidator outputValidator;
    private AutomationConfig config;
    private GeminiProvider provider;

    private static final String VALID_CASE_JSON = """
            {"title":"T","description":"D","sideAContent":"A","sideBContent":"B",
             "category":"Other","caseType":"classic","sideASubtitle":"a",
             "sideBSubtitle":"b","bothWrongSubtitle":"c","metadata":{}}
            """;

    private static final String VALID_PLAN_JSON = """
            {"interactions":[]}
            """;

    private static final String VALID_COMMENT_JSON = """
            {"content":"hello"}
            """;

    private static final String VALID_REPLY_JSON = """
            {"content":"reply"}
            """;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        rateLimiter = mock(RateLimiter.class);
        outputValidator = new OutputValidator();
        config = new AutomationConfig();
        config.getAi().setMaxOutputTokensCase(1024);
        config.getAi().setMaxOutputTokensComment(512);
        config.getAi().setMaxOutputTokensReply(512);
        config.getAi().setMaxOutputTokensPlan(2048);
        provider = new GeminiProvider(chatClient, rateLimiter, outputValidator, config);

        lenient().when(rateLimiter.acquire(anyInt())).thenReturn(Mono.empty());
    }

    private void stubChatResponse(String content) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn(content);
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().chatResponse())
                .thenReturn(response);
    }

    @Test
    void generateCase_returnsParsedCase() {
        stubChatResponse(VALID_CASE_JSON);

        GenerateCaseInput input = new GenerateCaseInput(
                "seed1", List.of("topicA"), 50, "es", List.of("ex1"), "live ctx");
        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("T");
        assertThat(result.caseType()).isEqualTo("classic");
        verify(rateLimiter).acquire(1024);
    }

    @Test
    void generateCase_nullSuccessExamplesAndLiveContext_buildsPrompt() {
        stubChatResponse(VALID_CASE_JSON);

        GenerateCaseInput input = new GenerateCaseInput(
                "seed", List.of(), 70, "en", null, null);
        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("T");
    }

    @Test
    void generateInteractionPlan_returnsPlan() {
        stubChatResponse(VALID_PLAN_JSON);

        GenerateInteractionPlanInput input = new GenerateInteractionPlanInput(
                "c1", "Title", "A", "B", "Other", 3, 50, "es", 10, 2);
        InteractionPlan plan = provider.generateInteractionPlan(input).block();

        assertThat(plan).isNotNull();
        assertThat(plan.interactions()).isEmpty();
        verify(rateLimiter).acquire(2048);
    }

    @Test
    void generateComment_returnsComment() {
        stubChatResponse(VALID_COMMENT_JSON);

        GenerateCommentInput input = new GenerateCommentInput(
                "c1", "Title", "A", "B", "neutral", 50, "es");
        GeneratedComment comment = provider.generateComment(input).block();

        assertThat(comment).isNotNull();
        assertThat(comment.content()).isEqualTo("hello");
        verify(rateLimiter).acquire(512);
    }

    @Test
    void generateReply_returnsReply() {
        stubChatResponse(VALID_REPLY_JSON);

        GenerateReplyInput input = new GenerateReplyInput(
                "c1", "Title", "parent content", "neutral", 50, "es");
        GeneratedReply reply = provider.generateReply(input).block();

        assertThat(reply).isNotNull();
        assertThat(reply.content()).isEqualTo("reply");
        verify(rateLimiter).acquire(512);
    }

    @Test
    void generateCase_rateLimited_propagatesError() {
        when(rateLimiter.acquire(anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("rate limited")));

        GenerateCaseInput input = new GenerateCaseInput(
                "s", List.of(), 50, "es", List.of());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> provider.generateCase(input).block()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate limited");
    }

    @Test
    void generateCase_wrappedMarkdownJson_parses() {
        stubChatResponse("```json\n" + VALID_CASE_JSON + "\n```");

        GenerateCaseInput input = new GenerateCaseInput(
                "s", List.of("t"), 50, "es", List.of());
        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("T");
    }

    @Test
    void generateCase_jsonWithSurroundingProse_parses() {
        stubChatResponse("Here is the case you asked for:\n" + VALID_CASE_JSON + "\nHope it helps!");

        GenerateCaseInput input = new GenerateCaseInput(
                "s", List.of(), 30, "es", List.of());
        GeneratedCase result = provider.generateCase(input).block();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("T");
    }
}
