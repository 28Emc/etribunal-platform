package com.etribunal.identity.user;

import com.etribunal.identity.follow.FollowRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints service-to-service (core-domain → identity). Protegidos por el
 * token interno compartido (X-Internal-Token); nunca expuestos vía gateway.
 */
@RestController
@RequestMapping("/users/internal")
public class InternalUsersController {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final byte[] internalTokenHash;

    public InternalUsersController(UserRepository userRepository,
                                   FollowRepository followRepository,
                                   @Value("${etribunal.internal.token}") String internalToken) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.internalTokenHash = sha256(internalToken);
    }

    @GetMapping("/summaries")
    public List<Map<String, Object>> summaries(
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam("ids") List<UUID> ids) {
        guard(token);

        return userRepository.findAllById(ids).stream()
                .filter(u -> u.getDeletedAt() == null)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId().toString(),
                        "username", u.getUsername(),
                        "avatarUrl", u.getAvatarUrl() != null ? u.getAvatarUrl() : "",
                        "anonymous", Boolean.TRUE.equals(u.getIsAnonymous())))
                .toList();
    }

    @GetMapping("/following-ids")
    public List<String> followingIds(
            @RequestHeader("X-Internal-Token") String token,
            @RequestHeader("X-User-Id") UUID userId) {
        guard(token);

        return followRepository.findFollowedIdsByFollower(userId).stream()
                .map(UUID::toString)
                .toList();
    }

    private void guard(String presented) {
        if (presented == null || !MessageDigest.isEqual(
                internalTokenHash, sha256(presented))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Token interno inválido");
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
