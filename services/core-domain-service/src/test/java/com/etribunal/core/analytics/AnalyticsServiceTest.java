package com.etribunal.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private InteractionLogRepository interactionLogRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(interactionLogRepository);
    }

    @Test
    void log_createsInteractionLog() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        analyticsService.log(InteractionAction.VIEW.name(), caseId, userId);

        verify(interactionLogRepository).save(any());
    }

    @Test
    void log_withMetadata_createsInteractionLog() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("key", "value");

        analyticsService.log(InteractionAction.COMMENT.name(), caseId, userId, metadata);

        verify(interactionLogRepository).save(any());
    }

    @Test
    void getGlobalKPIs_returnsMap() {
        lenient().when(interactionLogRepository.countByActionSince(any())).thenReturn(List.of());
        lenient().when(interactionLogRepository.countByUserIdIsNotNullAndCreatedAtAfter(any())).thenReturn(0L);

        Map<String, Object> result = analyticsService.getGlobalKPIs();

        assertThat(result).containsKeys("byAction", "active_users", "since");
    }
}
