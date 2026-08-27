package com.etribunal.ai.automation.domain.dtos;

import java.util.List;

public record GenerateInteractionPlanInput(
    String caseId,
    String title,
    String sideAContent,
    String sideBContent,
    String category,
    int interactionCount,
    int intensity,
    String language,
    int availableUsers,
    int maxPerUser
) {}