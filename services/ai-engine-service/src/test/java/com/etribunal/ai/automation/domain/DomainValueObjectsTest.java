package com.etribunal.ai.automation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainValueObjectsTest {

    @Test
    void intensityMapper_allBrackets() {
        assertThat(IntensityMapper.toDirective(0)).contains("Provocador");
        assertThat(IntensityMapper.toDirective(20)).contains("Provocador");
        assertThat(IntensityMapper.toDirective(21)).contains("Cr");
        assertThat(IntensityMapper.toDirective(40)).contains("Cr");
        assertThat(IntensityMapper.toDirective(41)).contains("Equilibrado");
        assertThat(IntensityMapper.toDirective(60)).contains("Equilibrado");
        assertThat(IntensityMapper.toDirective(61)).contains("Anal");
        assertThat(IntensityMapper.toDirective(80)).contains("Anal");
        assertThat(IntensityMapper.toDirective(81)).contains("Acad");
        assertThat(IntensityMapper.toDirective(100)).contains("Acad");
    }

    @Test
    void aiError_getters() {
        AiError err = new AiError(AiErrorCode.RATE_LIMITED, "slow down", true);
        assertThat(err.getCode()).isEqualTo(AiErrorCode.RATE_LIMITED);
        assertThat(err.isRetryable()).isTrue();
        assertThat(err.getMessage()).isEqualTo("slow down");
        assertThat(err).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aiError_notRetryable() {
        AiError err = new AiError(AiErrorCode.CONTENT_FILTERED, "blocked", false);
        assertThat(err.isRetryable()).isFalse();
    }

    @Test
    void enums_haveExpectedConstants() {
        assertThat(AiErrorCode.values()).contains(
                AiErrorCode.PROVIDER_ERROR, AiErrorCode.RATE_LIMITED,
                AiErrorCode.INVALID_OUTPUT, AiErrorCode.CONTENT_FILTERED,
                AiErrorCode.UNAVAILABLE, AiErrorCode.TIMEOUT);
        assertThat(AutomationInteractionType.values()).containsExactly(
                AutomationInteractionType.COMMENT, AutomationInteractionType.REPLY,
                AutomationInteractionType.REACTION, AutomationInteractionType.VOTE);
        assertThat(AutomationInteractionStatus.values()).contains(
                AutomationInteractionStatus.PENDING, AutomationInteractionStatus.SCHEDULED,
                AutomationInteractionStatus.PROCESSING, AutomationInteractionStatus.SUCCESS,
                AutomationInteractionStatus.FAILED, AutomationInteractionStatus.REJECTED,
                AutomationInteractionStatus.SKIPPED);
        assertThat(AutomationRunStatus.values()).contains(
                AutomationRunStatus.PENDING, AutomationRunStatus.RUNNING,
                AutomationRunStatus.COMPLETED, AutomationRunStatus.PARTIAL,
                AutomationRunStatus.FAILED, AutomationRunStatus.CANCELLED);
        assertThat(AutomationCaseStatus.values()).contains(
                AutomationCaseStatus.PLANNED, AutomationCaseStatus.CREATED,
                AutomationCaseStatus.REJECTED, AutomationCaseStatus.FAILED,
                AutomationCaseStatus.SKIPPED);
    }

    @Test
    void generateCaseInput_nullDefaults() {
        GenerateCaseInputHolder check = new GenerateCaseInputHolder();
        assertThat(check.successDefaultsToEmpty().successExamples()).isEmpty();
        assertThat(check.liveContextDefaultsToEmpty()).isEmpty();
    }

    private static class GenerateCaseInputHolder {
        com.etribunal.ai.automation.domain.dtos.GenerateCaseInput successDefaultsToEmpty() {
            return new com.etribunal.ai.automation.domain.dtos.GenerateCaseInput(
                    "s", java.util.List.of(), 1, "es", null, null);
        }

        String liveContextDefaultsToEmpty() {
            var in = new com.etribunal.ai.automation.domain.dtos.GenerateCaseInput(
                    "s", java.util.List.of(), 1, "es", null, null);
            return in.liveContext();
        }
    }

    @Test
    void generateCaseInput_fiveArgConstructor_setsEmptyLiveContext() {
        var in = new com.etribunal.ai.automation.domain.dtos.GenerateCaseInput(
                "seed", java.util.List.of("t"), 50, "es", java.util.List.of("ex"));
        assertThat(in.liveContext()).isEmpty();
        assertThat(in.successExamples()).containsExactly("ex");
    }
}
