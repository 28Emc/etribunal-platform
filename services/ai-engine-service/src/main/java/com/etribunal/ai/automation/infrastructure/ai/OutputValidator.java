package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.domain.AiError;
import com.etribunal.ai.automation.domain.AiErrorCode;
import com.etribunal.ai.automation.domain.dtos.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

public class OutputValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public <T> Mono<T> validate(String rawOutput, Class<T> targetType) {
        return Mono.defer(() -> {
            try {
                String cleaned = cleanJson(rawOutput);
                JsonNode node = objectMapper.readTree(cleaned);
                T result = objectMapper.treeToValue(node, targetType);
                return Mono.just(result);
            } catch (Exception e) {
                return Mono.error(new AiError(AiErrorCode.INVALID_OUTPUT, "Failed to parse AI output: " + e.getMessage(), true));
            }
        });
    }

    private String cleanJson(String raw) {
        String trimmed = raw.trim();
        // Remove markdown code fences if present
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastBackticks = trimmed.lastIndexOf("```");
            if (lastBackticks > 0) {
                trimmed = trimmed.substring(0, lastBackticks);
            }
        }
        // Try to extract JSON object
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}