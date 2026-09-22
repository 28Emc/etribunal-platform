package com.etribunal.ai.automation.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etribunal.common.security.JwtTokenProvider;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.MessagingException;

class AutomationWsChannelInterceptorTest {

    private static final JwtTokenProvider PROVIDER = new JwtTokenProvider(
            "test-access-secret-0123456789abcdef0123456789abcdef".getBytes(),
            "test-refresh-secret-0123456789abcdef0123456789abcdef".getBytes(),
            "etribunal",
            Duration.ofMinutes(15),
            Duration.ofDays(7));

    private final AutomationWsChannelInterceptor interceptor =
            new AutomationWsChannelInterceptor(PROVIDER);

    private static Message<byte[]> connectFrame(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void rejectsConnectWithoutAuthorizationHeader() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame(null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithInvalidToken() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Bearer invalid.token.here"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithNonAdminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "user", List.of("USER"));
        assertThatThrownBy(() -> interceptor.preSend(connectFrame(token), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsConnectWithAdminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "admin", List.of("USER", "ADMIN"));
        interceptor.preSend(connectFrame(token), null);
    }

    @Test
    void allowsConnectWithSysadminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "sysops", List.of("SYSADMIN"));
        interceptor.preSend(connectFrame(token), null);
    }

    @Test
    void allowsNonConnectMessagesWithoutChecks() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("s1");
        Message<byte[]> subscribe =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(subscribe, null);
    }
}