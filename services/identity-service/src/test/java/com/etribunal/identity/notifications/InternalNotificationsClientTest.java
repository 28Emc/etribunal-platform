package com.etribunal.identity.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.config.InternalApiProperties;
import com.etribunal.common.domain.notification.NotificationType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalNotificationsClientTest {

    @Mock
    RestClient.Builder restClientBuilder;

    @Mock
    RestClient restClient;

    @Mock
    RestClient.RequestBodyUriSpec uriSpec;

    @Mock
    RestClient.RequestBodySpec bodySpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    InternalNotificationsClient client;
    InternalApiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InternalApiProperties("http://localhost:8080", "http://localhost:8081", "internal-token");
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class));
        client = new InternalNotificationsClient(restClientBuilder, properties);
    }

    @Test
    void createNotificationCallsApiWithCorrectParams() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/notifications/internal/create")).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(org.springframework.http.ResponseEntity.ok().build());

        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        client.createNotification(userId, actorId, com.etribunal.common.domain.notification.NotificationType.NEW_COMMENT, Map.of("key", "value"));

        verify(restClient).post();
        verify(uriSpec).uri("/api/notifications/internal/create");
        verify(bodySpec).body(any());
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void createNotificationHandlesError() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/notifications/internal/create")).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("server error"));

        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        client.createNotification(userId, actorId, com.etribunal.common.domain.notification.NotificationType.NEW_COMMENT, Map.of());
    }
}