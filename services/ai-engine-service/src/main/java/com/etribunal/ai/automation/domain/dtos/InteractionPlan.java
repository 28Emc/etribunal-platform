package com.etribunal.ai.automation.domain.dtos;

import java.util.List;

public record InteractionPlan(
    List<PlannedInteraction> interactions
) {}