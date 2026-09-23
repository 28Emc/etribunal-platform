package com.etribunal.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private SysadminApiKeyGuard sysadminGuard;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        when(request.getHeader("X-Sysadmin-Api-Key")).thenReturn("valid-key");
        lenient().doNothing().when(sysadminGuard).assertAuthorized(anyString());
    }

    @Test
    void globalKPIs_returnsKPIs() {
        Map<String, Object> kpis = Map.of("totalInteractions", 100L, "activeUsers", 10L, "since", "2026-01-01");
        when(analyticsService.getGlobalKPIs()).thenReturn(kpis);

        ResponseEntity<?> response = controller.globalKPIs(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(analyticsService).getGlobalKPIs();
    }

    @Test
    void caseKPIs_returnsKPIs() {
        UUID caseId = UUID.randomUUID();
        Map<String, Object> kpis = Map.of("case_id", caseId.toString(), "byAction", Map.of());
        when(analyticsService.getCaseKPIs(caseId)).thenReturn(kpis);

        ResponseEntity<?> response = controller.caseKPIs(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(analyticsService).getCaseKPIs(caseId);
    }

    @Test
    void globalKPIs_validatesApiKey() {
        when(request.getHeader("X-Sysadmin-Api-Key")).thenReturn("valid-key");

        controller.globalKPIs(request);

        verify(sysadminGuard).assertAuthorized("valid-key");
    }

    @Test
    void caseKPIs_validatesApiKey() {
        when(request.getHeader("X-Sysadmin-Api-Key")).thenReturn("valid-key");

        controller.caseKPIs(UUID.randomUUID(), request);

        verify(sysadminGuard).assertAuthorized("valid-key");
    }
}