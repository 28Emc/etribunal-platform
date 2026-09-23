package com.etribunal.ai.automation.infrastructure.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

class AutomationSettingsServiceTest {

    private AutomationConfig config;
    private JdbcTemplate jdbc;
    private AutomationSettingsService svc;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        jdbc = mock(JdbcTemplate.class);
        svc = new AutomationSettingsService(jdbc, config);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
    }

    @Test
    void getSettings_returnsEnvDefaults() {
        Map<String, Object> settings = svc.getSettings();

        assertThat(settings)
                .containsEntry("enabled", true)
                .containsEntry("dryRun", false)
                .containsEntry("runHour", 9)
                .containsKey("engagementWeights")
                .containsKey("rssFeedUrls");
    }

    @Test
    void applyToConfig_appliesDbValues_overEnv() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "enabled", "value", "true"),
                Map.of("key", "dailyCasesMax", "value", "20")
        ));

        svc.applyToConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDailyCasesMax()).isEqualTo(20);
    }

    @Test
    void applyToConfig_handlesRssUrls() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "rssFeedUrls", "value", "[\"https://a.com/rss\",\"https://b.com/rss\"]")
        ));

        svc.applyToConfig();

        assertThat(config.getContext().getRssFeedUrls())
                .containsExactly("https://a.com/rss", "https://b.com/rss");
    }

    @Test
    void updateSettings_persistsAndReapplies() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "enabled", "value", "true"),
                Map.of("key", "schedulingWindowHours", "value", "48")
        ));

        Map<String, Object> result = svc.updateSettings(Map.of(
                "enabled", true,
                "schedulingWindowHours", 48
        ));

        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getSchedulingWindowHours()).isEqualTo(48);
        assertThat(result).containsEntry("enabled", true);
    }

    @Test
    void updateSettings_ignoresUnknownKeys() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        Map<String, Object> result = svc.updateSettings(Map.of("notARealKey", 1));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        assertThat(result).isNotNull();
    }

    @Test
    void updateSettings_nullChanges_returnsCurrent() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        Map<String, Object> result = svc.updateSettings(null);

        assertThat(result).containsEntry("enabled", true);
    }

    @Test
    void onStartup_appliesConfig() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "intensityMin", "value", "40"),
                Map.of("key", "dailyPoolSize", "value", "7")
        ));

        svc.onStartup();

        assertThat(config.getIntensityMin()).isEqualTo(40);
        assertThat(config.getDailyPoolSize()).isEqualTo(7);
    }

    @Test
    void applyToConfig_badRow_fallsBackToEnvDefaults() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "enabled", "value", "not-valid-json{"),
                Map.of("key", "runHour", "value", "11")
        ));

        svc.applyToConfig();

        assertThat(config.getRunHour()).isEqualTo(9);
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void applyToConfig_readAllFails_usesEnvDefaults() {
        when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("db down"));

        svc.applyToConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDailyCasesMax()).isEqualTo(5);
    }

    @Test
    void updateSettings_persistsJsonShapedStringAsIs() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        svc.updateSettings(Map.of("rssFeedUrls", "[\"https://c.com/rss\"]"));

        verify(jdbc).update(anyString(), eq("rssFeedUrls"), eq("[\"https://c.com/rss\"]"));
    }

    @Test
    void applyToConfig_appliesEngagementAndActivityKeys() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "activityWeighted", "value", "true"),
                Map.of("key", "engagementEnabled", "value", "true"),
                Map.of("key", "engagementTopExamples", "value", "12"),
                Map.of("key", "engagementEvaluationDays", "value", "14"),
                Map.of("key", "engagementVotesWeight", "value", "1"),
                Map.of("key", "engagementCommentsWeight", "value", "2"),
                Map.of("key", "engagementReactionsWeight", "value", "3"),
                Map.of("key", "engagementSharesWeight", "value", "4"),
                Map.of("key", "engagementSavesWeight", "value", "5"),
                Map.of("key", "engagementViewsWeight", "value", "6")
        ));

        svc.applyToConfig();

        assertThat(config.getActivity().isWeighted()).isTrue();
        assertThat(config.getEngagement().isEnabled()).isTrue();
        assertThat(config.getEngagement().getTopExamples()).isEqualTo(12);
        assertThat(config.getEngagement().getEvaluationDays()).isEqualTo(14);
        assertThat(config.getEngagement().getVotesWeight()).isEqualTo(1);
        assertThat(config.getEngagement().getCommentsWeight()).isEqualTo(2);
        assertThat(config.getEngagement().getReactionsWeight()).isEqualTo(3);
        assertThat(config.getEngagement().getSharesWeight()).isEqualTo(4);
        assertThat(config.getEngagement().getSavesWeight()).isEqualTo(5);
        assertThat(config.getEngagement().getViewsWeight()).isEqualTo(6);
    }

    @Test
    void applyToConfig_appliesRemainingKeys() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("key", "dryRun", "value", "true"),
                Map.of("key", "usersPerCaseMin", "value", "2"),
                Map.of("key", "usersPerCaseMax", "value", "6"),
                Map.of("key", "maxInteractionsPerUserPerCaseMin", "value", "1"),
                Map.of("key", "maxInteractionsPerUserPerCaseMax", "value", "4"),
                Map.of("key", "intensityMax", "value", "80"),
                Map.of("key", "schedulingIntervalMin", "value", "20"),
                Map.of("key", "schedulingIntervalMax", "value", "120"),
                Map.of("key", "schedulingWindowHours", "value", "18"),
                Map.of("key", "dailyCasesMin", "value", "2")
        ));

        svc.applyToConfig();

        assertThat(config.isDryRun()).isTrue();
        assertThat(config.getUsersPerCaseMin()).isEqualTo(2);
        assertThat(config.getUsersPerCaseMax()).isEqualTo(6);
        assertThat(config.getMaxInteractionsPerUserPerCaseMin()).isEqualTo(1);
        assertThat(config.getMaxInteractionsPerUserPerCaseMax()).isEqualTo(4);
        assertThat(config.getIntensityMax()).isEqualTo(80);
        assertThat(config.getSchedulingIntervalMin()).isEqualTo(20);
        assertThat(config.getSchedulingIntervalMax()).isEqualTo(120);
        assertThat(config.getSchedulingWindowHours()).isEqualTo(18);
        assertThat(config.getDailyCasesMin()).isEqualTo(2);
    }
}
