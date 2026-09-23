package com.etribunal.ai.automation.infrastructure.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class EngagementServiceExtendedTest {

    private JdbcTemplate jdbc;
    private AutomationConfig config;
    private EngagementService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        config = new AutomationConfig();
        config.getEngagement().setEnabled(true);
        config.getEngagement().setVotesWeight(4);
        config.getEngagement().setCommentsWeight(5);
        config.getEngagement().setReactionsWeight(3);
        config.getEngagement().setSharesWeight(6);
        config.getEngagement().setSavesWeight(4);
        config.getEngagement().setViewsWeight(1);
        service = new EngagementService(jdbc, config);
    }

    @Test
    void evaluateRecentCases_success_upsertsScores() throws Exception {
        String caseId = UUID.randomUUID().toString();
        EngagementService.CasePerformance perf = new EngagementService.CasePerformance(
                caseId, "Title", 0, 10, 5, 3, 1, 2, 100, LocalDate.now());

        org.mockito.ArgumentCaptor<RowMapper<EngagementService.CasePerformance>> mapperCaptor =
                org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), mapperCaptor.capture(), any())).thenReturn(List.of(perf));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        int count = service.evaluateRecentCases(7);

        assertThat(count).isEqualTo(1);
        verify(jdbc).update(anyString(), any(Object[].class));

        // Invoke RowMapper to cover its lambda body
        RowMapper<EngagementService.CasePerformance> mapper = mapperCaptor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("case_id")).thenReturn(caseId);
        when(rs.getString("title")).thenReturn("Title");
        when(rs.getInt("votes")).thenReturn(10);
        when(rs.getInt("comments")).thenReturn(5);
        when(rs.getInt("reactions")).thenReturn(3);
        when(rs.getInt("shares")).thenReturn(1);
        when(rs.getInt("saves")).thenReturn(2);
        when(rs.getInt("views")).thenReturn(100);

        EngagementService.CasePerformance mapped = mapper.mapRow(rs, 0);
        assertThat(mapped.caseId()).isEqualTo(caseId);
        assertThat(mapped.title()).isEqualTo("Title");
        assertThat(mapped.votes()).isEqualTo(10);
        assertThat(mapped.comments()).isEqualTo(5);
        assertThat(mapped.reactions()).isEqualTo(3);
        assertThat(mapped.shares()).isEqualTo(1);
        assertThat(mapped.saves()).isEqualTo(2);
        assertThat(mapped.views()).isEqualTo(100);
        assertThat(mapped.engagementScore()).isZero();
        assertThat(mapped.evaluationDate()).isNotNull();
    }

    @Test
    void evaluateRecentCases_updateFailure_returnsZero() {
        EngagementService.CasePerformance perf = new EngagementService.CasePerformance(
                UUID.randomUUID().toString(), "T", 0, 1, 1, 1, 1, 1, 1, LocalDate.now());
        when(jdbc.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of(perf));
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("upsert fail"));

        int count = service.evaluateRecentCases(7);

        assertThat(count).isZero();
    }

    @Test
    void evaluateRecentCases_daysClampedToMinOne() {
        when(jdbc.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of());

        service.evaluateRecentCases(-100);

        verify(jdbc).query(anyString(), any(RowMapper.class), any());
    }

    @Test
    void findTopPerformingCases_invokesRowMapper() throws Exception {
        String caseId = UUID.randomUUID().toString();
        org.mockito.ArgumentCaptor<RowMapper<EngagementService.CasePerformance>> mapperCaptor =
                org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), mapperCaptor.capture(), anyInt())).thenReturn(List.of());

        service.findTopPerformingCases(5);

        RowMapper<EngagementService.CasePerformance> mapper = mapperCaptor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("case_id")).thenReturn(caseId);
        when(rs.getString("title")).thenReturn("Top title");
        when(rs.getInt("engagement_score")).thenReturn(85);
        when(rs.getInt("votes")).thenReturn(10);
        when(rs.getInt("comments")).thenReturn(4);
        when(rs.getInt("reactions")).thenReturn(7);
        when(rs.getInt("shares")).thenReturn(2);
        when(rs.getInt("saves")).thenReturn(3);
        when(rs.getInt("views")).thenReturn(50);
        when(rs.getObject("evaluation_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));

        EngagementService.CasePerformance mapped = mapper.mapRow(rs, 0);
        assertThat(mapped.engagementScore()).isEqualTo(85);
        assertThat(mapped.evaluationDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void getAnalyticsSummary_partialFailure_stillReturnsMap() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class)))
                .thenThrow(new RuntimeException("count fail"));
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        Map<String, Object> summary = service.getAnalyticsSummary(3);

        assertThat(summary)
                .containsEntry("evaluatedCases", 0)
                .containsEntry("averageScore", 0)
                .containsEntry("topCases", List.of());
    }

    @Test
    void calculateScore_zeroWeightsConfig_stillWorks() {
        config.getEngagement().setVotesWeight(0);
        config.getEngagement().setCommentsWeight(0);
        config.getEngagement().setReactionsWeight(0);
        config.getEngagement().setSharesWeight(0);
        config.getEngagement().setSavesWeight(0);
        config.getEngagement().setViewsWeight(0);

        assertThat(service.calculateScore(100, 100, 100, 100, 100, 100)).isZero();
    }

    @Test
    void casePerformance_recordAccessors() {
        EngagementService.CasePerformance p = new EngagementService.CasePerformance(
                "cid", "t", 50, 1, 2, 3, 4, 5, 6, LocalDate.of(2026, 1, 1));
        assertThat(p.caseId()).isEqualTo("cid");
        assertThat(p.title()).isEqualTo("t");
        assertThat(p.engagementScore()).isEqualTo(50);
        assertThat(p.votes()).isEqualTo(1);
        assertThat(p.comments()).isEqualTo(2);
        assertThat(p.reactions()).isEqualTo(3);
        assertThat(p.shares()).isEqualTo(4);
        assertThat(p.saves()).isEqualTo(5);
        assertThat(p.views()).isEqualTo(6);
        assertThat(p.evaluationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
