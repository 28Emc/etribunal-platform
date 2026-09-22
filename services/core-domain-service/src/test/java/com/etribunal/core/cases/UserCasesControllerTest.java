package com.etribunal.core.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.cases.dto.CaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserCasesControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserCasesController controller;

    @Test
    void userCasesDelegatesToService() {
        when(caseService.getCasesByUsername("midu", 0, 10, null, request))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<CaseResponse>>> response =
                controller.userCases("midu", 0, 10, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
        verify(caseService).getCasesByUsername("midu", 0, 10, null, request);
    }

    @Test
    void trackShareLogsViewWithUtmParameters() {
        UUID userId = UUID.randomUUID();
        when(request.getParameter("utm_source")).thenReturn("wa");
        when(request.getParameter("utm_medium")).thenReturn("social");

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.trackShare(userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsEntry("tracked", true);
        verify(analyticsService).log(eq(InteractionAction.VIEW.name()), isNull(), isNull(), anyMap());
    }

    @Test
    void trackShareDefaultsSourceAndMedium() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.trackShare(userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(analyticsService).log(eq(InteractionAction.VIEW.name()), isNull(), isNull(), any());
    }
}