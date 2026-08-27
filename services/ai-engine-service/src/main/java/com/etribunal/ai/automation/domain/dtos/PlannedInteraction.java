package com.etribunal.ai.automation.domain.dtos;

import com.etribunal.ai.automation.domain.AutomationInteractionType;

public record PlannedInteraction(
    AutomationInteractionType type,
    String stance,
    Integer tone,
    String content,
    String reaction,
    String option,
    Integer replyToIndex
) {}