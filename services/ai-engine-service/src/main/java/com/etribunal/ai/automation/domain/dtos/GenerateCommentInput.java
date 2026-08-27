package com.etribunal.ai.automation.domain.dtos;

public record GenerateCommentInput(
    String caseId,
    String caseTitle,
    String caseSideA,
    String caseSideB,
    String stance,
    int tone,
    String language
) {}