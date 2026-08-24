package com.etribunal.identity.auth;

import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.common.domain.exception.UnauthorizedException;
import com.etribunal.common.security.JwtTokenProvider;
import com.etribunal.identity.auth.dto.LoginRequest;
import com.etribunal.identity.auth.dto.RefreshRequest;
import com.etribunal.identity.auth.dto.RegisterRequest;
import com.etribunal.identity.auth.dto.TokenResponse;
import com.etribunal.identity.auth.dto.UserResponse;
import com.etribunal.identity.config.JwtProperties;
import com.etribunal.identity.user.UserEntity;
import com.etribunal.identity.user.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    static final String ATTEMPTS_PREFIX = "auth:attempts:";
    static final String SESSION_PREFIX = "auth:session:";
    static final int MAX_ATTEMPTS = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
    }

    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El email ya está registrado");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("El username no está disponible");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(
                request.displayName() != null ? request.displayName().trim() : request.username());
        userRepository.save(user);

        return issueTokens(user);
    }

    public TokenResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();
        String normalizedEmail = identifier.toLowerCase();
        String attemptsKey = ATTEMPTS_PREFIX + normalizedEmail;

        String attemptsValue = redis.opsForValue().get(attemptsKey);
        if (attemptsValue != null && Integer.parseInt(attemptsValue) >= MAX_ATTEMPTS) {
            Long ttlSeconds = redis.getExpire(attemptsKey);
            long minutes = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds / 60 + 1 : 1;
            throw new UnauthorizedException("Cuenta bloqueada temporalmente. Intenta en " + minutes + " min");
        }

        UserEntity user =
                userRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(normalizedEmail, identifier)
                        .orElseGet(UserEntity::new);

        boolean valid =
                user.getId() != null
                        && user.isActive()
                        && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!valid) {
            registerFailure(attemptsKey);
            throw new UnauthorizedException("Credenciales inválidas");
        }

        redis.delete(attemptsKey);
        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        var claims =
                jwtTokenProvider
                        .parseRefreshToken(request.refreshToken())
                        .orElseThrow(() -> new UnauthorizedException("Refresh token inválido o expirado"));

        String userId = claims.getSubject();
        String storedJti = redis.opsForValue().get(SESSION_PREFIX + userId);
        if (storedJti == null || !storedJti.equals(claims.getJWTID())) {
            throw new UnauthorizedException("Sesión revocada");
        }

        UserEntity user =
                userRepository
                        .findById(UUID.fromString(userId))
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Cuenta inactiva");
        }

        redis.delete(SESSION_PREFIX + userId);
        return issueTokens(user);
    }

    public void logout(UUID userId) {
        redis.delete(SESSION_PREFIX + userId);
    }

    public UserResponse me(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(UserEntity::isActive)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private void registerFailure(String attemptsKey) {
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptsKey, ATTEMPT_WINDOW);
        }
    }

    private TokenResponse issueTokens(UserEntity user) {
        List<String> roles = List.of(user.getRole());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        var claims =
                jwtTokenProvider
                        .parseRefreshToken(refreshToken)
                        .orElseThrow(() -> new IllegalStateException("No se pudo validar el refresh emitido"));
        redis.opsForValue()
                .set(SESSION_PREFIX + user.getId(), claims.getJWTID(), jwtProperties.refreshTtl());

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessTtl().toSeconds(),
                UserResponse.from(user));
    }
}
