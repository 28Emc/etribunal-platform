package com.etribunal.identity.notifications;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.config.InternalApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class InternalNotificationsClientTest {

    @Mock
    RestClient.Builder restClientBuilder;

    @Mock
    RestClient restClient;

    InternalNotificationsClient client;
    InternalApiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InternalApiProperties("http://localhost:8080", "http://localhost:8081", "internal-token");
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        client = new InternalNotificationsClient(restClientBuilder, properties);
    }

    @Test
    void constructorWiresCoreBaseUrlAndInternalTokenHeader() {
        verify(restClientBuilder).baseUrl(properties.coreBaseUrl());
        verify(restClientBuilder).defaultHeader("X-Internal-Token", properties.token());
        verify(restClientBuilder).build();
    }
}