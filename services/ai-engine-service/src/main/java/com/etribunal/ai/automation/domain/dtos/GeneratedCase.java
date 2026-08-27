package com.etribunal.ai.automation.domain.dtos;

import java.util.Map;

public record GeneratedCase(
    String title,
    String description,
    String sideAContent,
    String sideBContent,
    String category,
    String caseType,
    String sideASubtitle,
    String sideBSubtitle,
    String bothWrongSubtitle,
    Map<String, Object> metadata
) {}