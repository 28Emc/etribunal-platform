package com.etribunal.identity.user;

import com.etribunal.common.domain.exception.BadRequestException;
import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.common.domain.notification.NotificationType;
import com.etribunal.identity.follow.FollowEntity;
import com.etribunal.identity.follow.FollowId;
import com.etribunal.identity.follow.FollowRepository;
import com.etribunal.identity.notifications.InternalNotificationsClient;
import com.etribunal.identity.user.dto.UpdateProfileRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final String ANON_USERNAME = "Anonymous Judge";
    static final String ANON_AVATAR = "https://secure.gravatar.com/avatar/0?d=mp&f=y";

    private static final String MSG_USER_NOT_FOUND = "Usuario no encontrado";
    private static final String KEY_FOLLOWING = "following";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_IS_ANONYMOUS = "is_anonymous";

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final InternalNotificationsClient notificationsClient;

    public UserService(UserRepository userRepository,
                       FollowRepository followRepository,
                       InternalNotificationsClient notificationsClient) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.notificationsClient = notificationsClient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myProfile(UUID userId) {
        UserEntity user = requireUser(userId);
        return ownView(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(String username, UUID requesterId) {
        UserEntity user =
                userRepository
                        .findByUsernameAndDeletedAtNull(username)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        Map<String, Object> view = maskedPublicView(user, requesterId);
        view.put("is_following", requesterId != null && isFollowing(requesterId, user.getId()));
        view.put("followersCount", followRepository.countByFollowingId(user.getId()));
        view.put("followingCount", followRepository.countByFollowerId(user.getId()));
        return view;
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, UpdateProfileRequest dto) {
        UserEntity user =
                userRepository
                        .findByIdAndDeletedAtNull(userId)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        if (dto.username() != null && !dto.username().equalsIgnoreCase(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCase(dto.username())) {
                throw new ConflictException("El username no está disponible");
            }
            user.setUsername(dto.username());
        }
        if (dto.bio() != null) {
            user.setBio(dto.bio());
        }
        if (dto.avatarUrl() != null) {
            user.setAvatarUrl(dto.avatarUrl());
        }
        if (dto.isAnonymous() != null) {
            user.setIsAnonymous(dto.isAnonymous());
        }
        if (dto.language() != null) {
            user.setLanguage(dto.language());
        }

        UserEntity saved = userRepository.save(user);
        return ownView(saved);
    }

    @Transactional
    public Map<String, Object> toggleFollow(UUID followerId, String targetUsername) {
        UserEntity target =
                userRepository
                        .findByUsernameAndDeletedAtNull(targetUsername)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        UserEntity follower =
                userRepository
                        .findByIdAndDeletedAtNull(followerId)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        if (Boolean.TRUE.equals(follower.getIsAnonymous())) {
            throw new BadRequestException("No puedes seguir a otros usuarios siendo anónimo");
        }
        if (followerId.equals(target.getId())) {
            throw new BadRequestException("No puedes seguirte a ti mismo");
        }

        FollowId id = new FollowId(followerId, target.getId());
        if (followRepository.existsById(id)) {
            followRepository.deleteById(id);
            return Map.of(KEY_FOLLOWING, false);
        }
        followRepository.save(new FollowEntity(follower, target));

        // Notify target user about new follower
        notificationsClient.createNotification(
                target.getId(),
                followerId,
                NotificationType.NEW_FOLLOWER,
                Map.of("follower_id", followerId.toString(),
                       "follower_username", follower.getUsername()));

        return Map.of(KEY_FOLLOWING, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> followers(String username, UUID requesterId) {
        UserEntity user =
                userRepository
                        .findByUsernameAndDeletedAtNull(username)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        return followRepository
                .findByFollowingIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                .stream()
                .map(
                        f -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("follower", maskedPublicView(f.getFollower(), requesterId));
                            row.put(KEY_CREATED_AT, f.getCreatedAt());
                            return row;
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> following(String username, UUID requesterId) {
        UserEntity user =
                userRepository
                        .findByUsernameAndDeletedAtNull(username)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        return followRepository
                .findByFollowerIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                .stream()
                .map(
                        f -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put(KEY_FOLLOWING, maskedPublicView(f.getFollowing(), requesterId));
                            row.put(KEY_CREATED_AT, f.getCreatedAt());
                            return row;
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myFollowing(UUID userId, int skip, int take) {
        int safeTake = Math.max(take, 1);
        int safeSkip = Math.max(skip, 0);
        Pageable page = PageRequest.of(safeSkip / safeTake, safeTake);
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(userId, page).stream()
                .map(
                        f -> {
                            UserEntity u = f.getFollowing();
                            boolean hide = hideAnonIdentity(u, userId);
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", u.getId());
                            row.put(KEY_USERNAME, hide ? ANON_USERNAME : u.getUsername());
                            row.put(KEY_AVATAR_URL, hide ? ANON_AVATAR : u.getAvatarUrl());
                            row.put(KEY_IS_ANONYMOUS, u.getIsAnonymous());
                            row.put("bio", hide ? null : u.getBio());
                            row.put("followers_count", followRepository.countByFollowingId(u.getId()));
                            return row;
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchUsers(String query, UUID requesterId,
                                                 int take, int skip) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) {
            return List.of();
        }
        int clamped = Math.clamp(take, 1, 50);
        int safeSkip = Math.max(skip, 0);
        int page = safeSkip / clamped;
        return userRepository.searchByUsername(normalized, PageRequest.of(page, clamped)).stream()
                .filter(u -> requesterId == null || !requesterId.equals(u.getId()))
                .limit(clamped)
                .map(u -> searchView(u, requesterId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> topJudges(int limit, UUID currentUserId) {
        int clamped = Math.clamp(limit, 1, 50);
        List<UserEntity> users = userRepository.findTopJudges(PageRequest.of(0, clamped + 1));

        var followingIds =
                currentUserId == null
                        ? java.util.Set.<UUID>of()
                        : java.util.Set.copyOf(
                                followRepository
                                        .findByFollowerIdOrderByCreatedAtDesc(
                                                currentUserId, Pageable.unpaged())
                                        .stream()
                                        .map(f -> f.getFollowing().getId())
                                        .toList());

        return users.stream()
                .filter(u -> currentUserId == null || !currentUserId.equals(u.getId()))
                .limit(clamped)
                .map(
                        u -> {
                            boolean hide = hideAnonIdentity(u, currentUserId);
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", u.getId());
                            row.put(KEY_USERNAME, hide ? ANON_USERNAME : u.getUsername());
                            row.put(KEY_AVATAR_URL, hide ? ANON_AVATAR : u.getAvatarUrl());
                            row.put(KEY_IS_ANONYMOUS, u.getIsAnonymous());
                            row.put("followers_count", followRepository.countByFollowingId(u.getId()));
                            if (currentUserId != null) {
                                row.put("is_following", followingIds.contains(u.getId()));
                            }
                            return row;
                        })
                .toList();
    }

    @Transactional
    public Map<String, Object> softDelete(UUID userId, String username) {
        UserEntity user =
                userRepository
                        .findByUsernameAndDeletedAtNull(username)
                        .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        if (!user.getId().equals(userId)) {
            throw new BadRequestException("No puedes eliminar la cuenta de otro usuario");
        }

        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        followRepository.deleteAll(followsOf(userId));
        return Map.of("success", true);
    }

    private List<FollowEntity> followsOf(UUID userId) {
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
    }

    private boolean isFollowing(UUID followerId, UUID followingId) {
        return followRepository.existsById(new FollowId(followerId, followingId));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository
                .findByIdAndDeletedAtNull(userId)
                .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));
    }

    private Map<String, Object> ownView(UserEntity user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put(KEY_USERNAME, user.getUsername());
        view.put("email", user.getEmail());
        view.put(KEY_AVATAR_URL, user.getAvatarUrl());
        view.put("bio", user.getBio());
        view.put("display_name", user.getDisplayName());
        view.put(KEY_IS_ANONYMOUS, user.getIsAnonymous());
        view.put("receive_notifications", user.getReceiveNotifications());
        view.put("language", user.getLanguage());
        view.put("role", user.getRole());
        view.put(KEY_CREATED_AT, user.getCreatedAt());
        view.put("hasPassword", user.getPasswordHash() != null && !user.getPasswordHash().isEmpty());
        return view;
    }

    private Map<String, Object> maskedPublicView(UserEntity user, UUID requesterId) {
        boolean hideIdentity =
                Boolean.TRUE.equals(user.getIsAnonymous())
                        && !user.getId().equals(requesterId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put(KEY_USERNAME, hideIdentity ? ANON_USERNAME : user.getUsername());
        view.put(KEY_AVATAR_URL, hideIdentity ? ANON_AVATAR : user.getAvatarUrl());
        view.put("bio", hideIdentity ? null : user.getBio());
        view.put(KEY_IS_ANONYMOUS, user.getIsAnonymous());
        view.put(KEY_CREATED_AT, user.getCreatedAt());
        return view;
    }

    private Map<String, Object> searchView(UserEntity user, UUID requesterId) {
        boolean hide = hideAnonIdentity(user, requesterId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put(KEY_USERNAME, hide ? ANON_USERNAME : user.getUsername());
        view.put(KEY_AVATAR_URL, hide ? ANON_AVATAR : user.getAvatarUrl());
        view.put("bio", hide ? null : user.getBio());
        return view;
    }

    private static boolean hideAnonIdentity(UserEntity user, UUID requesterId) {
        return Boolean.TRUE.equals(user.getIsAnonymous()) && !user.getId().equals(requesterId);
    }
}
