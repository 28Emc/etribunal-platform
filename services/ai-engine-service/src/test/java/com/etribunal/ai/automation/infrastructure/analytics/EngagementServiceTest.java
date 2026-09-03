package com.etribunal.ai.automation.infrastructure.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

class EngagementServiceTest {

    private AutomationConfig config;
    private EngagementService service;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.getEngagement().setEnabled(true);
        service = new EngagementService(mock(JdbcTemplate.class), config);
    }

    @Test
    void calculateScore_isZero_whenNoInteractions() {
        assertThat(service.calculateScore(0, 0, 0, 0, 0, 0)).isZero();
    }

    @Test
    void calculateScore_isPositiveAndBounded_whenInteractionsExist() {
        int score = service.calculateScore(10, 8, 6, 4, 3, 200);
        assertThat(score).isBetween(1, 100);
    }

    @Test
    void calculateScore_isWithin_0to100_forMassiveTraffic() {
        int score = service.calculateScore(10000, 5000, 3000, 2000, 1500, 500000);
        assertThat(score).isBetween(0, 100);
    }

    @Test
    void findTopPerformingCases_returnsEmpty_whenEmptyResults() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Integer.class)))
                .thenReturn(java.util.List.of());

        EngagementService svc = new EngagementService(jdbc, config);
        assertThat(svc.findTopPerformingCases(5)).isEmpty();
    }

    @Test
    void findTopPerformingCases_clampsLimit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Integer.class)))
                .thenReturn(java.util.List.of(
                        new EngagementService.CasePerformance("id-1", "Caso A", 80, 1, 1, 1, 0, 0, 10, LocalDate.now())
                ));

        EngagementService svc = new EngagementService(jdbc, config);
        assertThat(svc.findTopPerformingCases(0)).hasSize(1);
        assertThat(svc.findTopPerformingCases(200)).hasSize(1);
    }
}
