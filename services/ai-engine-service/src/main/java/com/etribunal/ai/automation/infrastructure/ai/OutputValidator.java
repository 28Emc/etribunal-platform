package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.domain.AiError;
import com.etribunal.ai.automation.domain.AiErrorCode;
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
        // Extraer el valor JSON más externo (objeto o array) sin cortarlo por prose
        // que contenga llaves/paréntesis dentro de strings o antes/después del JSON.
        String extracted = extractOutermostJson(trimmed);
        return extracted != null ? extracted : trimmed;
    }

    private static String extractOutermostJson(String text) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}