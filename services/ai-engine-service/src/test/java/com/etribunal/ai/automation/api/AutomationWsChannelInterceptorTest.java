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
        Message<byte[]> frame = connectFrame(null);
        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithInvalidToken() {
        Message<byte[]> frame = connectFrame("Bearer invalid.token.here");
        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithNonAdminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "user", List.of("USER"));
        Message<byte[]> frame = connectFrame(token);
        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsConnectWithAdminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "admin", List.of("USER", "ADMIN"));
        Message<byte[]> frame = connectFrame(token);
        org.assertj.core.api.Assertions.assertThat(interceptor.preSend(frame, null)).isNotNull();
    }

    @Test
    void allowsConnectWithSysadminToken() {
        String token = "Bearer " + PROVIDER.generateAccessToken(
                UUID.randomUUID(), "sysops", List.of("SYSADMIN"));
        Message<byte[]> frame = connectFrame(token);
        org.assertj.core.api.Assertions.assertThat(interceptor.preSend(frame, null)).isNotNull();
    }

    @Test
    void allowsNonConnectMessagesWithoutChecks() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("s1");
        Message<byte[]> subscribe =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        org.assertj.core.api.Assertions.assertThat(interceptor.preSend(subscribe, null)).isNotNull();
    }
}