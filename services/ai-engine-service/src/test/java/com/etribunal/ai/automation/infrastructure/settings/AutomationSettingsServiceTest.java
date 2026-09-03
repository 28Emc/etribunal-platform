package com.etribunal.ai.automation.infrastructure.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

        assertThat(settings).containsEntry("enabled", false);
        assertThat(settings).containsEntry("dryRun", true);
        assertThat(settings).containsEntry("runHour", 9);
        assertThat(settings).containsKey("engagementWeights");
        assertThat(settings).containsKey("rssFeedUrls");
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
}
