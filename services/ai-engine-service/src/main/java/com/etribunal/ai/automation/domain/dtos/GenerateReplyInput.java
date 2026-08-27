package com.etribunal.ai.automation.domain.dtos;

public record GenerateReplyInput(
    String caseId,
    String caseTitle,
    String parentCommentContent,
    String stance,
    int tone,
    String language
) {}