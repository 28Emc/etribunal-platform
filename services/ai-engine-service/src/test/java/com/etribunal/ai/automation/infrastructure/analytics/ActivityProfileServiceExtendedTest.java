package com.etribunal.ai.automation.infrastructure.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ActivityProfileServiceExtendedTest {

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
    }

    @Test
    void transitionPhase_whenSamplesBetweenThresholds() {
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(100);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt()))
                .thenReturn(List.of(Map.entry(9, 60), Map.entry(10, 40)));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.TRANSITION);
        assertThat(svc.metrics()).containsEntry("phase", "TRANSITION");
        assertThat(svc.metrics()).containsEntry("samples", 100);
        assertThat(svc.metrics()).containsKey("lastRefresh");
    }

    @Test
    void bootstrapPhase_whenVeryFewSamples() {
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(5);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt()))
                .thenReturn(List.of(Map.entry(12, 5)));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.BOOTSTRAP);
        double[] w = svc.currentWeights();
        assertThat(w[12]).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void withBotIds_usesExcludingQueries() {
        UUID botId = UUID.randomUUID();
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(botId.toString()));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(0);

        // count path with bots uses query(sql, pss, extractor); hourly uses query(sql, pss, extractor) too
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(500)
                .thenReturn(List.of(Map.entry(8, 300), Map.entry(9, 200)));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.STABLE);
        verify(identityJdbc).queryForList(anyString(), eq(String.class));
    }

    @Test
    void botFilterSetter_setsParamsCorrectly() throws Exception {
        when(identityJdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(10);

        org.mockito.ArgumentCaptor<PreparedStatementSetter> pssCaptor =
                org.mockito.ArgumentCaptor.forClass(PreparedStatementSetter.class);
        when(jdbc.query(anyString(), pssCaptor.capture(), any(ResultSetExtractor.class)))
                .thenReturn(10)
                .thenReturn(List.of());

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        // Invoke the captured PSS to cover botFilterSetter body
        PreparedStatementSetter pss = pssCaptor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = mock(Connection.class);
        when(ps.getConnection()).thenReturn(conn);
        Array arr = mock(Array.class);
        when(conn.createArrayOf(eq("uuid"), any())).thenReturn(arr);

        pss.setValues(ps);

        verify(ps).setInt(1, 7);
        verify(ps).setArray(2, arr);
    }

    private static class ArgumentCaptorHolder {}

    @Test
    void identityDbFailure_returnsEmptyBotIds() {
        when(identityJdbc.queryForList(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("identity down"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.BOOTSTRAP);
    }

    @Test
    void hourlyRowsNull_returnsEmptyMap() {
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(null);

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.BOOTSTRAP);
    }

    @Test
    void persistFailure_stillUpdatesWeights() {
        when(identityJdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyInt())).thenReturn(500);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt()))
                .thenReturn(List.of(Map.entry(20, 400), Map.entry(21, 100)));
        doThrow(new RuntimeException("persist fail")).when(jdbc).update(anyString(), any(Object[].class));

        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        svc.refresh();

        assertThat(svc.phase()).isEqualTo(ActivityProfileService.ProfilePhase.STABLE);
        double[] w = svc.currentWeights();
        assertThat(w[20]).isGreaterThan(1.0 / 24);
    }

    @Test
    void weightForHour_wrapsNegativeHours() {
        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        assertThat(svc.weightForHour(-1)).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(svc.weightForHour(24)).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(svc.weightForHour(-25)).isCloseTo(1.0 / 24, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void currentWeights_returnsCopy() {
        ActivityProfileService svc = new ActivityProfileService(jdbc, identityJdbc, config);
        double[] w1 = svc.currentWeights();
        w1[0] = 999;
        double[] w2 = svc.currentWeights();
        assertThat(w2[0]).isNotEqualTo(999);
    }

    @Test
    void profileResult_equalityHashCodeToString() throws Exception {
        var clazz = Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfileResult");
        var ctor = clazz.getDeclaredConstructor(double[].class,
                Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfilePhase"));
        ctor.setAccessible(true);
        double[] weights = new double[24];
        Object phase = Enum.valueOf(
                (Class<Enum>) Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfilePhase"),
                "STABLE");
        Object r1 = ctor.newInstance(weights, phase);
        Object r2 = ctor.newInstance(weights, phase);

        assertThat(r1)
                .isEqualTo(r2)
                .hasSameHashCodeAs(r2);
        assertThat(r1.toString()).contains("STABLE");
        assertThat(r1.equals(r1)).isTrue();
        assertThat(r1.equals("other")).isFalse();

        double[] other = new double[24];
        other[0] = 1;
        Object r3 = ctor.newInstance(other, phase);
        assertThat(r1.equals(r3)).isFalse();
    }

    @Test
    void profileResult_differentPhase_notEqual() throws Exception {
        var clazz = Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfileResult");
        var ctor = clazz.getDeclaredConstructor(double[].class,
                Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfilePhase"));
        ctor.setAccessible(true);
        double[] weights = new double[24];
        Class<?> phaseClass = Class.forName("com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService$ProfilePhase");
        Object stable = Enum.valueOf((Class<Enum>) phaseClass, "STABLE");
        Object bootstrap = Enum.valueOf((Class<Enum>) phaseClass, "BOOTSTRAP");
        Object r1 = ctor.newInstance(weights, stable);
        Object r2 = ctor.newInstance(weights, bootstrap);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void profilePhase_values() {
        assertThat(ActivityProfileService.ProfilePhase.values())
                .containsExactly(
                        ActivityProfileService.ProfilePhase.BOOTSTRAP,
                        ActivityProfileService.ProfilePhase.TRANSITION,
                        ActivityProfileService.ProfilePhase.STABLE);
    }
}
