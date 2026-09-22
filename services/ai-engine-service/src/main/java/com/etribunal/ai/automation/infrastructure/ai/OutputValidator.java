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
        int start = indexOfOpeningBrace(text);
        if (start < 0) {
            return null;
        }
        int end = indexOfMatchingClose(text, start);
        return end < 0 ? null : text.substring(start, end + 1);
    }

    private static int indexOfOpeningBrace(String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                i = skipJsonString(text, i);
                if (i < 0) {
                    return -1;
                }
                i++;
            } else if (c == '{' || c == '[') {
                return i;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static int indexOfMatchingClose(String text, int openIndex) {
        int depth = 0;
        int i = openIndex;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                i = skipJsonString(text, i);
                if (i < 0) {
                    return -1;
                }
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static int skipJsonString(String text, int openQuoteIndex) {
        boolean escaped = false;
        for (int j = openQuoteIndex + 1; j < text.length(); j++) {
            char c = text.charAt(j);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return j;
            }
        }
        return -1;
    }
}