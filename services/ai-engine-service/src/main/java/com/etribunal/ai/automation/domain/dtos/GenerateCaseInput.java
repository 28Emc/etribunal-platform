package com.etribunal.ai.automation.domain.dtos;

import java.util.List;
import java.util.Map;

public record GenerateCaseInput(
    String variationSeed,
    List<String> recentTopics,
    int intensity,
    String language
) {}