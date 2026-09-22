package com.etribunal.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.exception.BadRequestException;
import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.identity.follow.FollowEntity;
import com.etribunal.identity.follow.FollowId;
import com.etribunal.identity.follow.FollowRepository;
import com.etribunal.identity.notifications.InternalNotificationsClient;
import com.etribunal.identity.user.dto.UpdateProfileRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    FollowRepository followRepository;

    @Mock
    InternalNotificationsClient notificationsClient;

    UserService userService;

    UserEntity userA;
    UserEntity userB;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, followRepository, notificationsClient);

        userA = user("ana_t", "ana@test.com", false);
        userB = user("beto_j", "beto@test.com", false);
        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserEntity user(String username, String email, boolean anonymous) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setIsAnonymous(anonymous);
        return u;
    }

    @Test
    void myProfileIncludesHasPasswordAndEmail() {
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));

        var view = userService.myProfile(userA.getId());

        assertThat(view)
            .containsEntry("hasPassword", Boolean.TRUE)
            .containsEntry("email", "ana@test.com");
        assertThat(view.toString()).doesNotContain("hashed");
    }

    @Test
    void profileMasksAnonymousUserForOtherRequester() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.of(anon));
        when(followRepository.countByFollowingId(anon.getId())).thenReturn(3L);
        when(followRepository.countByFollowerId(anon.getId())).thenReturn(1L);

        var view = userService.profile("ghost_1", userA.getId());

        assertThat(view)
            .containsEntry("username", UserService.ANON_USERNAME)
            .containsEntry("avatar_url", UserService.ANON_AVATAR);
        assertThat(view.get("bio")).isNull();
        assertThat(view)
            .containsEntry("followersCount", 3L)
            .containsEntry("is_following", Boolean.FALSE);
    }

    @Test
    void profileShowsOwnIdentityWhenAnonymousSelf() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.of(anon));
        when(followRepository.countByFollowingId(anon.getId())).thenReturn(0L);
        when(followRepository.countByFollowerId(anon.getId())).thenReturn(0L);

        var view = userService.profile("ghost_1", anon.getId());

        assertThat(view).containsEntry("username", "ghost_1");
    }

    @Test
    void updateProfileRejectsDuplicateUsername() {
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));
        when(userRepository.existsByUsernameIgnoreCase("beto_j")).thenReturn(true);

        UUID id = userA.getId();
        UpdateProfileRequest request =
                new UpdateProfileRequest(null, null, "beto_j", null, null);
        assertThatThrownBy(() -> userService.updateProfile(id, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("username");
    }

    @Test
    void updateProfileAppliesChanges() {
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));

        var view =
                userService.updateProfile(
                        userA.getId(),
                        new UpdateProfileRequest("nueva bio", "https://x/y.png", null, true, "en"));

        assertThat(view)
            .containsEntry("bio", "nueva bio")
            .containsEntry("avatar_url", "https://x/y.png")
            .containsEntry("is_anonymous", true)
            .containsEntry("language", "en");
    }

    @Test
    void toggleFollowSelfRejected() {
        when(userRepository.findByUsernameAndDeletedAtNull("ana_t"))
                .thenReturn(Optional.of(userA));
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.toggleFollow(id, "ana_t"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ti mismo");
    }

    @Test
    void toggleFollowAnonymousRejected() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        when(userRepository.findByIdAndDeletedAtNull(anon.getId())).thenReturn(Optional.of(anon));

        UUID id = anon.getId();
        assertThatThrownBy(() -> userService.toggleFollow(id, "beto_j"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("anónimo");
    }

    @Test
    void toggleFollowCreatesThenRemoves() {
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));
        when(followRepository.existsById(new FollowId(userA.getId(), userB.getId())))
                .thenReturn(false, true);

        assertThat(userService.toggleFollow(userA.getId(), "beto_j"))
                .containsEntry("following", true);
        verify(followRepository).save(any(FollowEntity.class));

        assertThat(userService.toggleFollow(userA.getId(), "beto_j"))
                .containsEntry("following", false);
        verify(followRepository).deleteById(new FollowId(userA.getId(), userB.getId()));
    }

    @Test
    void softDeleteRejectsForeignAccount() {
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.softDelete(id, "beto_j"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("otro usuario");

        verify(userRepository, never()).save(any());
    }

    @Test
    void softDeleteMarksDeletedAtAndCleansFollows() {
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());

        var result = userService.softDelete(userB.getId(), "beto_j");

        assertThat(result).containsEntry("success", true);
        assertThat(userB.getDeletedAt()).isNotNull();
    }

    @Test
    void searchRequiresMinTwoChars() {
        assertThat(userService.searchUsers("a", null, 8, 0)).isEmpty();
        verify(userRepository, never()).searchByUsername(any(), any());
    }

    @Test
    void topJudgesExcludesSelfWhenAuthenticated() {
        UserEntity other = user("carl_m", "c@test.com", false);
        when(userRepository.findTopJudges(any())).thenReturn(List.of(other, userA));
        when(followRepository.countByFollowingId(other.getId())).thenReturn(9L);

        var result = userService.topJudges(10, userA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("username", "carl_m");
        assertThat(result.get(0)).containsEntry("followers_count", 9L);
    }

    @Test
    void searchMasksAnonymousIdentity() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.searchByUsername(any(), any())).thenReturn(List.of(anon));

        var result = userService.searchUsers("ghost", null, 8, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("username", UserService.ANON_USERNAME);
        assertThat(result.get(0)).containsEntry("avatar_url", UserService.ANON_AVATAR);
        assertThat(result.get(0).get("bio")).isNull();
    }

    @Test
    void topJudgesMasksAnonymousIdentity() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findTopJudges(any())).thenReturn(List.of(anon));
        when(followRepository.countByFollowingId(anon.getId())).thenReturn(1L);

        var result = userService.topJudges(10, null);

        assertThat(result.get(0)).containsEntry("username", UserService.ANON_USERNAME);
    }

    @Test
    void myProfileThrowsWhenUserNotFound() {
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.empty());

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.myProfile(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    void profileThrowsWhenUserNotFound() {
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.profile("ghost_1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfileChangesUsernameWhenAvailable() {
        UserEntity current = user("old_name", "o@test.com", false);
        when(userRepository.findByIdAndDeletedAtNull(current.getId()))
                .thenReturn(Optional.of(current));
        when(userRepository.existsByUsernameIgnoreCase("new_user")).thenReturn(false);

        var view =
                userService.updateProfile(
                        current.getId(), new UpdateProfileRequest(null, null, "new_user", null, null));

        assertThat(current.getUsername()).isEqualTo("new_user");
        assertThat(view).containsEntry("username", "new_user");
    }

    @Test
    void toggleFollowThrowsWhenTargetNotFound() {
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.empty());

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.toggleFollow(id, "ghost_1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void toggleFollowThrowsWhenRequesterNotFound() {
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        when(userRepository.findByIdAndDeletedAtNull(userA.getId())).thenReturn(Optional.empty());

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.toggleFollow(id, "beto_j"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void followersMasksAnonymousForOtherRequester() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        FollowEntity follow = mock(FollowEntity.class);
        when(follow.getFollower()).thenReturn(anon);
        when(follow.getCreatedAt()).thenReturn(Instant.ofEpochMilli(1000));
        when(followRepository.findByFollowingIdOrderByCreatedAtDesc(
                        userB.getId(), Pageable.unpaged()))
                .thenReturn(List.of(follow));

        var result = userService.followers("beto_j", userA.getId());

        assertThat(result).hasSize(1);
        Map<String, Object> follower =
                (Map<String, Object>) result.get(0).get("follower");
        assertThat(follower).containsEntry("username", UserService.ANON_USERNAME);
        assertThat(result.get(0)).containsEntry("created_at", Instant.ofEpochMilli(1000));
    }

    @Test
    void followersThrowsWhenUserNotFound() {
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.followers("ghost_1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void followingListsProfiles() {
        when(userRepository.findByUsernameAndDeletedAtNull("ana_t"))
                .thenReturn(Optional.of(userA));
        FollowEntity follow = new FollowEntity(userA, userB);
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(
                        userA.getId(), Pageable.unpaged()))
                .thenReturn(List.of(follow));

        var result = userService.following("ana_t", userA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("following");
    }

    @Test
    void myFollowingPaginatesAndMasksAnonymous() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        FollowEntity follow = new FollowEntity(userA, anon);
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(
                        userA.getId(), PageRequest.of(0, 20)))
                .thenReturn(List.of(follow));

        var result = userService.myFollowing(userA.getId(), 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("username", UserService.ANON_USERNAME);
        assertThat(result.get(0)).containsEntry("is_anonymous", true);
    }

    @Test
    void searchUsersExcludesRequester() {
        when(userRepository.searchByUsername(any(), any())).thenReturn(List.of(userA, userB));

        var result = userService.searchUsers("ana", userA.getId(), 8, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("username", "beto_j");
    }

    @Test
    void topJudgesIncludesIsFollowingFlag() {
        when(userRepository.findTopJudges(PageRequest.of(0, 11))).thenReturn(List.of(userB));
        FollowEntity follow = new FollowEntity(userA, userB);
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(
                        userA.getId(), Pageable.unpaged()))
                .thenReturn(List.of(follow));
        when(followRepository.countByFollowingId(userB.getId())).thenReturn(2L);

        var result = userService.topJudges(10, userA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("is_following", true);
    }

    @Test
    void softDeleteThrowsWhenUserNotFound() {
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.empty());

        UUID id = userA.getId();
        assertThatThrownBy(() -> userService.softDelete(id, "ghost_1"))
                .isInstanceOf(NotFoundException.class);
    }
}
