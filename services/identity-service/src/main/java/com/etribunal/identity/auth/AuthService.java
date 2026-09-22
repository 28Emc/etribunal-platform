package com.etribunal.identity.auth;

import com.etribunal.common.domain.exception.BadRequestException;
import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.common.domain.exception.UnauthorizedException;
import com.etribunal.common.security.JwtTokenProvider;
import com.etribunal.identity.auth.dto.ChangePasswordRequest;
import com.etribunal.identity.auth.dto.ForgotPasswordRequest;
import com.etribunal.identity.auth.dto.LoginRequest;
import com.etribunal.identity.auth.dto.RefreshRequest;
import com.etribunal.identity.auth.dto.RegisterRequest;
import com.etribunal.identity.auth.dto.ResetPasswordRequest;
import com.etribunal.identity.auth.dto.TokenResponse;
import com.etribunal.identity.auth.dto.UserResponse;
import com.etribunal.identity.config.JwtProperties;
import com.etribunal.identity.email.EmailProvider;
import com.etribunal.identity.email.EmailTemplates;
import com.etribunal.identity.user.UserEntity;
import com.etribunal.identity.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MSG_USER_NOT_FOUND = "Usuario no encontrado";

    static final String ATTEMPTS_PREFIX = "auth:attempts:";
    static final String SESSION_PREFIX = "auth:session:";
    static final int MAX_ATTEMPTS = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final EmailProvider emailProvider;
    private final EmailTemplates emailTemplates;
    private final String frontendUrl;
    private volatile String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            StringRedisTemplate redis,
            EmailProvider emailProvider,
            EmailTemplates emailTemplates,
            @Value("${etribunal.frontend.url:http://localhost:3000}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
        this.emailProvider = emailProvider;
        this.emailTemplates = emailTemplates;
        this.frontendUrl = frontendUrl;
    }

    private String buildEmailUrl(String path, String rawToken) {
        return frontendUrl + path + "?token=" + rawToken;
    }

    /** Hash SHA-256 de los tokens (verif/reset) antes de persistir o consultar. */
    static String hashToken(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hash exponencialmente costoso que iguala el tiempo de login cuando no hay usuario. */
    private String dummyPasswordHash() {
        String current = dummyPasswordHash;
        if (current == null) {
            current = passwordEncoder.encode(UUID.randomUUID() + "@" + System.nanoTime());
            dummyPasswordHash = current;
        }
        return current;
    }

    // ──────────────────────── Register ────────────────────────

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
        user.setAvatarUrl(
                "https://api.dicebear.com/7.x/identicon/svg?seed=" + request.username());

        String verificationToken = generateToken();
        user.setVerificationToken(hashToken(verificationToken));
        user.setVerificationExpires(Instant.now().plus(VERIFICATION_TOKEN_TTL));

        userRepository.save(user);

        String verificationUrl = buildEmailUrl("/verify-email", verificationToken);
        emailProvider.sendEmail(
                email,
                "Verifica tu correo - eTribunal",
                emailTemplates.verificationBody(verificationUrl, user.getLanguage()));

        return issueTokens(user);
    }

    // ──────────────────────── Login ────────────────────────

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        String attemptsKey = ATTEMPTS_PREFIX + email;

        String attemptsValue = redis.opsForValue().get(attemptsKey);
        int attempts = parseAttempts(attemptsValue);
        if (attempts >= MAX_ATTEMPTS) {
            Long ttlSeconds = redis.getExpire(attemptsKey);
            long minutes = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds / 60 + 1 : 1;
            throw new UnauthorizedException(
                    "Cuenta bloqueada temporalmente. Intenta en " + minutes + " min");
        }

        UserEntity user =
                userRepository
                        .findByEmailIgnoreCaseAndDeletedAtNull(email)
                        .orElseGet(UserEntity::new);

        // Igualar el tiempo de respuesta aunque el usuario no exista (evita enumeración por timing).
        // La comparación BCrypt se ejecuta SIEMPRE, usando un hash dummy cuando no existe usuario.
        String candidateHash =
                user.getPasswordHash() != null ? user.getPasswordHash() : dummyPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), candidateHash);
        boolean valid = user.getId() != null && user.isActive() && matches;
        if (!valid) {
            registerFailure(attemptsKey);
            throw new UnauthorizedException("Credenciales inválidas");
        }

        redis.delete(attemptsKey);
        return issueTokens(user);
    }

    private static int parseAttempts(String attemptsValue) {
        if (attemptsValue == null || attemptsValue.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(attemptsValue.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ──────────────────────── Refresh ────────────────────────

    public TokenResponse refresh(RefreshRequest request) {
        var claims =
                jwtTokenProvider
                        .parseRefreshToken(request.refreshToken())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                "Refresh token inválido o expirado"));

        String userId = claims.getSubject();
        String storageKey = SESSION_PREFIX + userId;
        String storedJti = redis.opsForValue().get(storageKey);
        String tokenJti = claims.getJWTID();
        log.debug("Refresh: userId={}, tokenJti={}, storedJti={}", userId, tokenJti, storedJti);

        if (storedJti == null || storedJti.isBlank()) {
            log.warn("Refresh rejected: sin sesión activa userId={}, tokenJti={}", userId, tokenJti);
            throw new UnauthorizedException("Sesión revocada");
        }

        // La sesión guarda "jtiActual|jtiAnterior": se acepta el jti actual o el
        // inmediatamente anterior en rotación. Esto evita que otra pestaña/dispositivo
        // con un refresh token ya rotado cierre la sesión completa ("Sesión revocada").
        java.util.Set<String> validJtis = java.util.Set.of(storedJti.split("\\|"));
        if (!validJtis.contains(tokenJti)) {
            log.warn("Refresh rejected: userId={}, tokenJti={}, storedJti={}", userId, tokenJti, storedJti);
            throw new UnauthorizedException("Sesión revocada");
        }

        UserEntity user =
                userRepository
                        .findById(UUID.fromString(userId))
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new UnauthorizedException("Cuenta inactiva");
        }

        // Mantener el jti "sobrante" (el otro de la lista) como anterior en la nueva
        // sesión: así una petición que aún usa el token recién reemplazado no se cae.
        String previousJti =
                validJtis.stream().filter(j -> !j.equals(tokenJti)).findFirst().orElse(null);

        // issueTokens sobrescribe la clave de sesión de forma atómica: NO borrar antes
        // (un delete previo + set posterior dejaría a dos pestañas revocándose en carrera).
        return issueTokens(user, previousJti);
    }

    // ──────────────────────── Logout ────────────────────────

    public void logout(UUID userId) {
        redis.delete(SESSION_PREFIX + userId);
    }

    // ──────────────────────── Me ────────────────────────

    public UserResponse me(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(UserEntity::isActive)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));
    }

    // ──────────────────────── Change Password ────────────────────────

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(
                    "Las cuentas sociales no tienen contraseña. Debes establecer una primero.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("La contraseña actual es incorrecta");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        redis.delete(SESSION_PREFIX + userId);
    }

    // ──────────────────────── Forgot Password ────────────────────────

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository
                .findByEmailIgnoreCaseAndDeletedAtNull(email)
                .ifPresent(user -> {
                    if (user.getPasswordHash() == null) {
                        log.info(" forgotPassword: {} usa auth social, omitiendo", email);
                        return;
                    }
                    String token = generateToken();
                    user.setResetToken(hashToken(token));
                    user.setResetTokenExpires(Instant.now().plus(RESET_TOKEN_TTL));
                    userRepository.save(user);

                    String resetUrl = buildEmailUrl("/reset-password", token);
                    emailProvider.sendEmail(
                            email,
                            "Recuperar tu contraseña - eTribunal",
                            emailTemplates.passwordResetBody(resetUrl, user.getLanguage()));
                });
    }

    // ──────────────────────── Reset Password ────────────────────────

    public void resetPassword(ResetPasswordRequest request) {
        UserEntity user =
                userRepository
                        .findByResetToken(hashToken(request.token()))
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Token inválido o expirado"));

        if (user.getResetTokenExpires() == null
                || user.getResetTokenExpires().isBefore(Instant.now())) {
            throw new BadRequestException("Token inválido o expirado");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpires(null);
        userRepository.save(user);
        redis.delete(SESSION_PREFIX + user.getId());
    }

    // ──────────────────────── Verify Email ────────────────────────

    public void verifyEmail(String token) {
        UserEntity user =
                userRepository
                        .findByVerificationToken(hashToken(token))
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "El enlace de verificación no es válido o ya fue utilizado."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        if (user.getVerificationExpires() == null
                || user.getVerificationExpires().isBefore(Instant.now())) {
            throw new BadRequestException(
                    "El enlace de verificación ha expirado. Solicita un nuevo enlace.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationExpires(null);
        userRepository.save(user);
    }

    // ──────────────────────── Resend Verification ────────────────────────

    public void resendVerificationEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        userRepository
                .findByEmailIgnoreCaseAndDeletedAtNull(normalizedEmail)
                .ifPresent(user -> {
                    if (Boolean.TRUE.equals(user.getEmailVerified())) {
                        return;
                    }
                    if (user.getPasswordHash() == null) {
                        return;
                    }
                    String token = generateToken();
                    user.setVerificationToken(hashToken(token));
                    user.setVerificationExpires(Instant.now().plus(VERIFICATION_TOKEN_TTL));
                    userRepository.save(user);

                    String verificationUrl = buildEmailUrl("/verify-email", token);
                    emailProvider.sendEmail(
                            normalizedEmail,
                            "Verifica tu correo - eTribunal",
                            emailTemplates.verificationBody(verificationUrl, user.getLanguage()));
                });
    }

    // ──────────────────────── Check Existence ────────────────────────

    public void checkExistence(String email, String username) {
        if (email != null && userRepository.existsByEmailIgnoreCase(email.trim().toLowerCase())) {
            log.debug("check-existence: email found");
        }
        if (username != null && userRepository.existsByUsernameIgnoreCase(username.trim())) {
            log.debug("check-existence: username found");
        }
    }

    // ──────────────────────── Internal helpers ────────────────────────

    private void registerFailure(String attemptsKey) {
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptsKey, ATTEMPT_WINDOW);
        }
    }

    private TokenResponse issueTokens(UserEntity user) {
        return issueTokens(user, null);
    }

    private TokenResponse issueTokens(UserEntity user, String previousJti) {
        List<String> roles = List.of(user.getRole());
        String accessToken =
                jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken =
                jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        var claims =
                jwtTokenProvider
                        .parseRefreshToken(refreshToken)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No se pudo validar el refresh emitido"));
        String sessionValue =
                previousJti == null || previousJti.isBlank()
                        ? claims.getJWTID()
                        : claims.getJWTID() + "|" + previousJti;
        redis.opsForValue()
                .set(
                        SESSION_PREFIX + user.getId(),
                        sessionValue,
                        jwtProperties.refreshTtl());

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessTtl().toSeconds(),
                UserResponse.from(user));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
