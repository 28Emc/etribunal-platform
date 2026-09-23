package com.etribunal.ai.automation.infrastructure.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AutomationConfig config;

    @Mock
    private AutomationConfig.EngagementConfig engagementConfig;

    @InjectMocks
    private EngagementService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.getEngagement()).thenReturn(engagementConfig);
        lenient().when(engagementConfig.getVotesWeight()).thenReturn(4);
        lenient().when(engagementConfig.getCommentsWeight()).thenReturn(5);
        lenient().when(engagementConfig.getReactionsWeight()).thenReturn(3);
        lenient().when(engagementConfig.getSharesWeight()).thenReturn(6);
        lenient().when(engagementConfig.getSavesWeight()).thenReturn(4);
        lenient().when(engagementConfig.getViewsWeight()).thenReturn(1);
        lenient().when(engagementConfig.isEnabled()).thenReturn(true);
        lenient().when(engagementConfig.getEvaluationDays()).thenReturn(7);
        lenient().when(engagementConfig.getTopExamples()).thenReturn(3);
    }

    @Test
    void calculateScore_returnsZero_whenAllZero() {
        int score = service.calculateScore(0, 0, 0, 0, 0, 0);

        assertThat(score).isZero();
    }

    @Test
    void calculateScore_returnsPositive_whenHasInteractions() {
        int score = service.calculateScore(10, 5, 3, 2, 1, 100);

        assertThat(score).isGreaterThan(0).isLessThanOrEqualTo(100);
    }

    @Test
    void calculateScore_max100_whenHighInteractions() {
        int score = service.calculateScore(1000, 500, 500, 500, 500, 5000);

        assertThat(score).isLessThanOrEqualTo(100);
    }

    @Test
    void calculateScore_increasesWithVotes() {
        int scoreLow = service.calculateScore(1, 0, 0, 0, 0, 0);
        int scoreHigh = service.calculateScore(10, 0, 0, 0, 0, 0);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void calculateScore_increasesWithComments() {
        int scoreLow = service.calculateScore(0, 1, 0, 0, 0, 0);
        int scoreHigh = service.calculateScore(0, 10, 0, 0, 0, 0);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void calculateScore_increasesWithReactions() {
        int scoreLow = service.calculateScore(0, 0, 1, 0, 0, 0);
        int scoreHigh = service.calculateScore(0, 0, 10, 0, 0, 0);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void calculateScore_increasesWithShares() {
        int scoreLow = service.calculateScore(0, 0, 0, 1, 0, 0);
        int scoreHigh = service.calculateScore(0, 0, 0, 10, 0, 0);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void calculateScore_increasesWithSaves() {
        int scoreLow = service.calculateScore(0, 0, 0, 0, 1, 0);
        int scoreHigh = service.calculateScore(0, 0, 0, 0, 10, 0);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void calculateScore_increasesWithViews() {
        int scoreLow = service.calculateScore(0, 0, 0, 0, 0, 1);
        int scoreHigh = service.calculateScore(0, 0, 0, 0, 0, 10);

        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @Test
    void findTopPerformingCases_returnsList() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of());

        List<EngagementService.CasePerformance> result = service.findTopPerformingCases(5);

        assertThat(result).isEmpty();
    }

    @Test
    void findTopPerformingCases_clampsTopN() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of());

        service.findTopPerformingCases(0);
        service.findTopPerformingCases(100);

        verify(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(1));
        verify(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(50));
    }

    @Test
    void findTopPerformingCases_returnsEmptyOnError() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        List<EngagementService.CasePerformance> result = service.findTopPerformingCases(5);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluateRecentCases_returnsZeroOnError() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenThrow(new RuntimeException("DB error"));

        int count = service.evaluateRecentCases(7);

        assertThat(count).isZero();
    }

    @Test
    void evaluateRecentCases_usesMax1Day() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of());

        int count = service.evaluateRecentCases(-5);

        assertThat(count).isZero();
        verify(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void getAnalyticsSummary_returnsMapWithKeys() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(10, 75);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> summary = service.getAnalyticsSummary(5);

        assertThat(summary)
                .containsKeys("evaluatedCases", "averageScore", "topCases")
                .containsEntry("evaluatedCases", 10)
                .containsEntry("averageScore", 75);
    }

    @Test
    void getAnalyticsSummary_handlesNullFromDb() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> summary = service.getAnalyticsSummary(5);

        assertThat(summary)
                .containsEntry("evaluatedCases", 0)
                .containsEntry("averageScore", 0);
    }

    @Test
    void getAnalyticsSummary_handlesDbError() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("DB error"));

        Map<String, Object> summary = service.getAnalyticsSummary(5);

        assertThat(summary)
                .containsEntry("evaluatedCases", 0)
                .containsEntry("averageScore", 0)
                .containsEntry("topCases", List.of());
    }
}
