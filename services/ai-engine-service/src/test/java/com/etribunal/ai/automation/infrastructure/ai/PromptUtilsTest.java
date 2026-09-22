package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptUtilsTest {

    @Test
    void caseGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.caseGenerationPrompt("es", 50);

        assertThat(prompt).contains("REGLAS DE SEGURIDAD OBLIGATORIAS");
        assertThat(prompt).contains("SCHEMA JSON OBLIGATORIO");
        assertThat(prompt).contains("title");
        assertThat(prompt).contains("sideAContent");
        assertThat(prompt).contains("sideBContent");
        assertThat(prompt).contains("category");
        assertThat(prompt).contains("caseType");
        assertThat(prompt).contains("Responde SOLO con JSON válido");
    }

    @Test
    void caseGenerationPrompt_containsLanguage() {
        String prompt = PromptUtils.caseGenerationPrompt("en", 50);

        assertThat(prompt).contains("en");
    }

    @Test
    void interactionPlanningPrompt_containsSchema() {
        String prompt = PromptUtils.interactionPlanningPrompt();

        assertThat(prompt).contains("COMMENT");
        assertThat(prompt).contains("REPLY");
        assertThat(prompt).contains("REACTION");
        assertThat(prompt).contains("VOTE");
        assertThat(prompt).contains("replyToIndex");
        assertThat(prompt).contains("Responde SOLO con un JSON");
    }

    @Test
    void commentGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.commentGenerationPrompt("es", 50);

        assertThat(prompt).contains("REGLAS DE SEGURIDAD OBLIGATORIAS");
        assertThat(prompt).contains("content");
        assertThat(prompt).contains("Responde SOLO con JSON");
    }

    @Test
    void replyGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.replyGenerationPrompt("es", 50);

        assertThat(prompt).contains("REGLAS DE SEGURIDAD OBLIGATORIAS");
        assertThat(prompt).contains("content");
        assertThat(prompt).contains("Responde SOLO con JSON");
    }

    @Test
    void moderationSafeWriting_containsRequiredRules() {
        assertThat(PromptUtils.MODERATION_SAFE_WRITING).contains("NO generes contenido sexualmente expl");
        assertThat(PromptUtils.MODERATION_SAFE_WRITING).contains("NO inventes datos personales reales");
        assertThat(PromptUtils.MODERATION_SAFE_WRITING).contains("debate");
    }

    @Test
    void caseJsonSchema_containsRequiredFields() {
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("title");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("sideAContent");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("sideBContent");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("category");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("caseType");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("sideASubtitle");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("sideBSubtitle");
        assertThat(PromptUtils.CASE_JSON_SCHEMA).contains("bothWrongSubtitle");
    }

    @Test
    void interactionPlanningPrompt_containsModerationRules() {
        String prompt = PromptUtils.interactionPlanningPrompt();

        assertThat(prompt).contains("NO generes contenido sexualmente expl");
        assertThat(prompt).contains("NO inventes datos personales reales");
    }
}