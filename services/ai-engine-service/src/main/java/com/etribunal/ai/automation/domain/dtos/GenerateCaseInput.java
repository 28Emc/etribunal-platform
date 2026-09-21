package com.etribunal.ai.automation.domain.dtos;

import java.util.List;

public record GenerateCaseInput(
    String variationSeed,
    List<String> recentTopics,
    int intensity,
    String language,
    List<String> successExamples,
    String liveContext
) {

    public GenerateCaseInput {
        successExamples = successExamples == null ? List.of() : successExamples;
        liveContext = liveContext == null ? "" : liveContext;
    }

    public GenerateCaseInput(
            String variationSeed,
            List<String> recentTopics,
            int intensity,
            String language,
            List<String> successExamples
    ) {
        this(variationSeed, recentTopics, intensity, language, successExamples, "");
    }
}