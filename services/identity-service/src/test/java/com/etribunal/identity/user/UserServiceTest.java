package com.etribunal.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.exception.BadRequestException;
import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.identity.follow.FollowEntity;
import com.etribunal.identity.follow.FollowId;
import com.etribunal.identity.follow.FollowRepository;
import com.etribunal.identity.user.dto.UpdateProfileRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    UserService userService;

    UserEntity userA;
    UserEntity userB;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, followRepository);

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

        assertThat(view.get("hasPassword")).isEqualTo(Boolean.TRUE);
        assertThat(view.get("email")).isEqualTo("ana@test.com");
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

        assertThat(view.get("username")).isEqualTo(UserService.ANON_USERNAME);
        assertThat(view.get("avatar_url")).isEqualTo(UserService.ANON_AVATAR);
        assertThat(view.get("bio")).isNull();
        assertThat(view.get("followersCount")).isEqualTo(3L);
        assertThat(view.get("is_following")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void profileShowsOwnIdentityWhenAnonymousSelf() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("ghost_1"))
                .thenReturn(Optional.of(anon));
        when(followRepository.countByFollowingId(anon.getId())).thenReturn(0L);
        when(followRepository.countByFollowerId(anon.getId())).thenReturn(0L);

        var view = userService.profile("ghost_1", anon.getId());

        assertThat(view.get("username")).isEqualTo("ghost_1");
    }

    @Test
    void updateProfileRejectsDuplicateUsername() {
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));
        when(userRepository.existsByUsernameIgnoreCase("beto_j")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                userService.updateProfile(
                                        userA.getId(), new UpdateProfileRequest(null, null, "beto_j", null, null)))
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

        assertThat(view.get("bio")).isEqualTo("nueva bio");
        assertThat(view.get("avatar_url")).isEqualTo("https://x/y.png");
        assertThat(view.get("is_anonymous")).isEqualTo(true);
        assertThat(view.get("language")).isEqualTo("en");
    }

    @Test
    void toggleFollowSelfRejected() {
        when(userRepository.findByUsernameAndDeletedAtNull("ana_t"))
                .thenReturn(Optional.of(userA));
        when(userRepository.findByIdAndDeletedAtNull(userA.getId()))
                .thenReturn(Optional.of(userA));

        assertThatThrownBy(() -> userService.toggleFollow(userA.getId(), "ana_t"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ti mismo");
    }

    @Test
    void toggleFollowAnonymousRejected() {
        UserEntity anon = user("ghost_1", "g@test.com", true);
        when(userRepository.findByUsernameAndDeletedAtNull("beto_j"))
                .thenReturn(Optional.of(userB));
        when(userRepository.findByIdAndDeletedAtNull(anon.getId())).thenReturn(Optional.of(anon));

        assertThatThrownBy(() -> userService.toggleFollow(anon.getId(), "beto_j"))
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

        assertThatThrownBy(() -> userService.softDelete(userA.getId(), "beto_j"))
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
        assertThat(userService.searchUsers("a", null, 8)).isEmpty();
        verify(userRepository, never()).searchByUsername(any(), any());
    }

    @Test
    void topJudgesExcludesSelfWhenAuthenticated() {
        UserEntity other = user("carl_m", "c@test.com", false);
        when(userRepository.findTopJudges(any())).thenReturn(List.of(other, userA));
        when(followRepository.countByFollowingId(other.getId())).thenReturn(9L);

        var result = userService.topJudges(10, userA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("username")).isEqualTo("carl_m");
        assertThat(result.get(0).get("followers_count")).isEqualTo(9L);
    }
}
