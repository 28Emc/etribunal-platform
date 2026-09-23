package com.etribunal.ai.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.api.AutomationWsChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import io.swagger.v3.oas.models.OpenAPI;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketAndOpenApiConfigTest {

    @Test
    void webSocketConfig_configureMessageBroker() {
        AutomationWsChannelInterceptor interceptor = mock(AutomationWsChannelInterceptor.class);
        WebSocketConfig cfg = new WebSocketConfig(interceptor, "http://localhost:3000, http://localhost:5173");

        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class, RETURNS_SELF);
        cfg.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    void webSocketConfig_inboundChannel_addsInterceptor() {
        AutomationWsChannelInterceptor interceptor = mock(AutomationWsChannelInterceptor.class);
        WebSocketConfig cfg = new WebSocketConfig(interceptor, "http://localhost:3000");

        ChannelRegistration registration = mock(ChannelRegistration.class, RETURNS_SELF);
        cfg.configureClientInboundChannel(registration);

        verify(registration).interceptors(interceptor);
    }

    @Test
    void webSocketConfig_registerEndpoints_usesOrigins() {
        AutomationWsChannelInterceptor interceptor = mock(AutomationWsChannelInterceptor.class);
        WebSocketConfig cfg = new WebSocketConfig(interceptor, "http://a.com, http://b.com");

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        var endpoint = mock(org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        when(registry.addEndpoint("/ws/automation")).thenReturn(endpoint);

        cfg.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws/automation");
        verify(endpoint).setAllowedOriginPatterns("http://a.com", "http://b.com");
        verify(endpoint).withSockJS();
    }

    @Test
    void webSocketConfig_singleOrigin() {
        AutomationWsChannelInterceptor interceptor = mock(AutomationWsChannelInterceptor.class);
        WebSocketConfig cfg = new WebSocketConfig(interceptor, "http://only.com");

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        var endpoint = mock(org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        when(registry.addEndpoint(anyString())).thenReturn(endpoint);

        cfg.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOriginPatterns("http://only.com");
    }

    @Test
    void webSocketConfig_emptyOrigins_filtered() {
        AutomationWsChannelInterceptor interceptor = mock(AutomationWsChannelInterceptor.class);
        WebSocketConfig cfg = new WebSocketConfig(interceptor, "http://a.com,,  ,http://b.com");

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        var endpoint = mock(org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        when(registry.addEndpoint(anyString())).thenReturn(endpoint);

        cfg.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOriginPatterns("http://a.com", "http://b.com");
    }

    @Test
    void openApiConfig_buildsOpenApi() {
        OpenApiConfig cfg = new OpenApiConfig();
        OpenAPI api = cfg.aiEngineOpenAPI();

        assertThat(api).isNotNull();
        assertThat(api.getInfo().getTitle()).isEqualTo("eTribunal AI Engine Service");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(api.getSecurity()).isNotEmpty();
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("Bearer JWT");
        assertThat(api.getComponents().getSecuritySchemes().get("Bearer JWT").getScheme()).isEqualTo("bearer");
    }
}
