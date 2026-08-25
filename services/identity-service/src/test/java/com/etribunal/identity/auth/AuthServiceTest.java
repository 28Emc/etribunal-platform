package com.etribunal.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.UnauthorizedException;
import com.etribunal.common.security.JwtTokenProvider;
import com.etribunal.identity.auth.dto.LoginRequest;
import com.etribunal.identity.auth.dto.RefreshRequest;
import com.etribunal.identity.auth.dto.RegisterRequest;
import com.etribunal.identity.auth.dto.TokenResponse;
import com.etribunal.identity.config.JwtProperties;
import com.etribunal.identity.user.UserEntity;
import com.etribunal.identity.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ACCESS_SECRET = "0123456789abcdef0123456789abcdef-access";
    private static final String REFRESH_SECRET = "fedcba9876543210fedcba9876543210-refresh";

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    AuthService authService;

    UserEntity existingUser;

    @BeforeEach
    void setUp() {
        JwtProperties props =
                new JwtProperties(
                        ACCESS_SECRET,
                        REFRESH_SECRET,
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        authService =
                new AuthService(
                        userRepository,
                        passwordEncoder,
                        new JwtTokenProvider(
                                ACCESS_SECRET.getBytes(),
                                REFRESH_SECRET.getBytes(),
                                "etribunal",
                                Duration.ofMinutes(15),
                                Duration.ofDays(7)),
                        props,
                        redisTemplate);

        existingUser = new UserEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("ana@test.com");
        existingUser.setUsername("ana_t");
        existingUser.setPasswordHash("hashed");
        existingUser.setDisplayName("Ana");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        lenient()
                .when(userRepository.save(any()))
                .thenAnswer(inv -> {
                    UserEntity u = inv.getArgument(0);
                    u.setId(UUID.randomUUID());
                    return u;
                });
    }

    @Test
    void registerCreatesUserAndReturnsTokens() {
        when(userRepository.existsByEmailIgnoreCase("ana@test.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("ana_t")).thenReturn(false);

        TokenResponse response =
                authService.register(new RegisterRequest("ANA@test.com", "ana_t", "Password1", "Ana"));

        assertThat(response.user().email()).isEqualTo("ana@test.com");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ana@test.com")).thenReturn(true);

        assertThatThrownBy(
                        () -> authService.register(new RegisterRequest("ana@test.com", "other", "Password1", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByEmailIgnoreCase("nueva@test.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("ana_t")).thenReturn(true);

        assertThatThrownBy(
                        () -> authService.register(new RegisterRequest("nueva@test.com", "ana_t", "Password1", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("username");
    }

    @Test
    void loginSuccessClearsAttemptsAndReturnsTokens() {
        when(valueOperations.get(AuthService.ATTEMPTS_PREFIX + "ana")).thenReturn(null);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtNullOrUsernameIgnoreCaseAndDeletedAtNull("ana", "ana"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password1", "hashed")).thenReturn(true);

        TokenResponse response = authService.login(new LoginRequest("ana", "Password1"));

        assertThat(response.user().username()).isEqualTo("ana_t");
        verify(redisTemplate).delete(AuthService.ATTEMPTS_PREFIX + "ana");
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void loginBadPasswordRegistersAttempt() {
        when(valueOperations.get(AuthService.ATTEMPTS_PREFIX + "ana")).thenReturn(null);
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString()))
                .thenAnswer(inv -> counter.incrementAndGet());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtNullOrUsernameIgnoreCaseAndDeletedAtNull("ana", "ana"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Credenciales inválidas");

        verify(redisTemplate).expire(anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void loginBlockedAfterMaxAttempts() {
        when(valueOperations.get(AuthService.ATTEMPTS_PREFIX + "ana@test.com")).thenReturn("5");
        when(redisTemplate.getExpire(AuthService.ATTEMPTS_PREFIX + "ana@test.com")).thenReturn(600L);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.com", "anything")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("bloqueada");

        verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedAtNullOrUsernameIgnoreCaseAndDeletedAtNull(any(), any());
    }

    @Test
    void refreshRotatesSession() {
        JwtTokenProvider provider =
                new JwtTokenProvider(
                        ACCESS_SECRET.getBytes(),
                        REFRESH_SECRET.getBytes(),
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        existingUser.setId(UUID.randomUUID());
        String refreshToken =
                provider.generateRefreshToken(existingUser.getId(), existingUser.getUsername());
        String jti = provider.parseRefreshToken(refreshToken).orElseThrow().getJWTID();

        when(valueOperations.get(AuthService.SESSION_PREFIX + existingUser.getId())).thenReturn(jti);
        when(userRepository.findById(existingUser.getId())).thenReturn(Optional.of(existingUser));

        // usar el provider real del servicio (mismos secretos)
        setField(authService, "jwtTokenProvider", provider);

        TokenResponse response = authService.refresh(new RefreshRequest(refreshToken));

        assertThat(response.refreshToken()).isNotEqualTo(refreshToken);
        verify(redisTemplate).delete(AuthService.SESSION_PREFIX + existingUser.getId());
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void refreshRejectedWhenJtiDoesNotMatch() {
        JwtTokenProvider provider =
                new JwtTokenProvider(
                        ACCESS_SECRET.getBytes(),
                        REFRESH_SECRET.getBytes(),
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        UUID userId = UUID.randomUUID();
        String refreshToken = provider.generateRefreshToken(userId, "ana_t");
        setField(authService, "jwtTokenProvider", provider);

        when(valueOperations.get(AuthService.SESSION_PREFIX + userId)).thenReturn("otro-jti");

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(refreshToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("revocada");
    }

    @Test
    void logoutDeletesSession() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId);
        verify(redisTemplate).delete(AuthService.SESSION_PREFIX + userId);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
