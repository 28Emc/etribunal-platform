package com.etribunal.ai.automation;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AutomationModuleTest {

    @Autowired
    private AutomationConfig config;

    @Test
    void contextLoads() {
        assertThat(config).isNotNull();
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void configRangesAreValid() {
        assertThat(config.getDailyCasesMin()).isPositive();
        assertThat(config.getDailyCasesMax()).isGreaterThanOrEqualTo(config.getDailyCasesMin());
        assertThat(config.getUsersPerCaseMin()).isPositive();
        assertThat(config.getUsersPerCaseMax()).isGreaterThanOrEqualTo(config.getUsersPerCaseMin());
        assertThat(config.getIntensityMin()).isGreaterThanOrEqualTo(0);
        assertThat(config.getIntensityMax()).isLessThanOrEqualTo(100);
    }
}