package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptUtilsTest {

    @Test
    void caseGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.caseGenerationPrompt("es", 50);

        assertThat(prompt).contains(
                "REGLAS DE SEGURIDAD OBLIGATORIAS",
                "SCHEMA JSON OBLIGATORIO",
                "title",
                "sideAContent",
                "sideBContent",
                "category",
                "caseType",
                "Responde SOLO con JSON válido");
    }

    @Test
    void caseGenerationPrompt_containsLanguage() {
        String prompt = PromptUtils.caseGenerationPrompt("en", 50);

        assertThat(prompt).contains("en");
    }

    @Test
    void interactionPlanningPrompt_containsSchema() {
        String prompt = PromptUtils.interactionPlanningPrompt();

        assertThat(prompt).contains("COMMENT", "REPLY", "REACTION", "VOTE", "replyToIndex", "Responde SOLO con un JSON");
    }

    @Test
    void commentGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.commentGenerationPrompt("es", 50);

        assertThat(prompt).contains("REGLAS DE SEGURIDAD OBLIGATORIAS", "content", "Responde SOLO con JSON");
    }

    @Test
    void replyGenerationPrompt_containsRequiredFields() {
        String prompt = PromptUtils.replyGenerationPrompt("es", 50);

        assertThat(prompt).contains("REGLAS DE SEGURIDAD OBLIGATORIAS", "content", "Responde SOLO con JSON");
    }

    @Test
    void moderationSafeWriting_containsRequiredRules() {
        assertThat(PromptUtils.MODERATION_SAFE_WRITING)
                .contains("NO generes contenido sexualmente expl", "NO inventes datos personales reales", "debate");
    }

    @Test
    void caseJsonSchema_containsRequiredFields() {
        assertThat(PromptUtils.CASE_JSON_SCHEMA)
                .contains("title", "sideAContent", "sideBContent", "category", "caseType",
                        "sideASubtitle", "sideBSubtitle", "bothWrongSubtitle");
    }

    @Test
    void interactionPlanningPrompt_containsModerationRules() {
        String prompt = PromptUtils.interactionPlanningPrompt();

        assertThat(prompt).contains("NO generes contenido sexualmente expl", "NO inventes datos personales reales");
    }
}