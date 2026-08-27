package com.etribunal.core.moderation;

import com.etribunal.core.cases.ModerationStatus;
import java.util.List;
import java.util.Map;

public record ModerationResult(
        ModerationStatus status,
        double riskScore,
        List<String> matchedRules,
        Map<String, Object> metadata) {
}