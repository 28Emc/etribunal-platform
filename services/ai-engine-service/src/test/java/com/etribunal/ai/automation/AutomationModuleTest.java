package com.etribunal.ai.automation;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AutomationCaseStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.ai.automation.domain.AutomationRunStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationModuleTest {

    @Test
    void enumsHaveExpectedValues() {
        assertThat(AutomationRunStatus.values()).hasSize(6);
        assertThat(AutomationCaseStatus.values()).hasSize(5);
        assertThat(AutomationInteractionStatus.values()).hasSize(7);
        assertThat(AutomationInteractionType.values()).hasSize(4);
    }

    @Test
    void configRangesAreValid() {
        AutomationConfig config = new AutomationConfig();
        config.setDailyCasesMin(1);
        config.setDailyCasesMax(5);

        assertThat(config.getDailyCasesMin()).isPositive();
        assertThat(config.getDailyCasesMax()).isGreaterThanOrEqualTo(config.getDailyCasesMin());

        int picked = config.pickDailyCases();
        assertThat(picked).isBetween(1, 5);
    }
}