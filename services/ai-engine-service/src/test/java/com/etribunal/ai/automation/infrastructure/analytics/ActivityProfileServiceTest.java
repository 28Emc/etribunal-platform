package com.etribunal.ai.automation.infrastructure.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

class ActivityProfileServiceTest {

    private AutomationConfig config;
    private JdbcTemplate jdbc;
    private JdbcTemplate identityJdbc;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.getActivity().setEnabled(true);
        config.getActivity().setWeighted(true);
        config.getActivity().setMinStableSamples(200);
        config.getActivity().setMinTransitionSamples(20);
        config.getActivity().setLookbackDays(7);
        jdbc = mock(JdbcTemplate.class);
        identityJdbc = mock(JdbcTemplate.class);
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
    }

    @Test
    void bootstrapsToUniform_whenNoData() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(0);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of());

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.BOOTSTRAP);
        double[] w = svc.currentWeights();
        assertThat(w).hasSize(24);
        assertThat(w[0]).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void becomesStable_whenEnoughSamples() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(500);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of(Map.entry(12, 400), Map.entry(13, 400)));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.STABLE);
        double[] w = svc.currentWeights();
        assertThat(w[12]).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(w[13]).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void weightForHour_returnsUniformBeforeRefresh() {
        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        for (int h = 0; h < 24; h++) {
            assertThat(svc.weightForHour(h)).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void refresh_handlesDbError_keepingPreviousWeights() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        // Se mantiene uniforme (no lanza)
        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.BOOTSTRAP);
        assertThat(svc.weightForHour(0)).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
    }
}
