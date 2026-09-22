package com.etribunal.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.identity.follow.FollowRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InternalUsersControllerTest {

    private static final String SECRET = "internal-secret";

    @Mock
    UserRepository userRepository;

    @Mock
    FollowRepository followRepository;

    @Mock
    UserService userService;

    InternalUsersController controller;

    @BeforeEach
    void setUp() {
        controller =
                new InternalUsersController(userRepository, followRepository, userService, SECRET);
    }

    @Test
    void constructorRejectsBlankToken() {
        assertThatThrownBy(
                        () ->
                                new InternalUsersController(
                                        userRepository, followRepository, userService, " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void searchRejectsWrongToken() {
        assertThatThrownBy(() -> controller.search("wrong", "q", 8, 0, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchRejectsNullToken() {
        assertThatThrownBy(() -> controller.search(null, "q", 8, 0, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchDelegatesWithRequester() {
        UUID requester = UUID.randomUUID();
        when(userService.searchUsers("ana", requester, 8, 2)).thenReturn(List.of());

        controller.search(SECRET, "ana", 8, 2, requester.toString());

        verify(userService).searchUsers("ana", requester, 8, 2);
    }

    @Test
    void searchIgnoresMalformedRequesterHeader() {
        when(userService.searchUsers("ana", null, 8, 0)).thenReturn(List.of());

        controller.search(SECRET, "ana", 8, 0, "not-a-uuid");

        verify(userService).searchUsers("ana", null, 8, 0);
    }

    @Test
    void summariesIncludeOnlyNonDeletedUsers() {
        UserEntity deleted = new UserEntity();
        deleted.setId(UUID.randomUUID());
        deleted.setUsername("ghost_1");
        deleted.setDeletedAt(Instant.now());
        UserEntity active = new UserEntity();
        active.setId(UUID.randomUUID());
        active.setUsername("ana_t");
        active.setIsAnonymous(true);

        when(userRepository.findAllById(List.of(deleted.getId(), active.getId())))
                .thenReturn(List.of(deleted, active));

        var result = controller.summaries(SECRET, List.of(deleted.getId(), active.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("username", "ana_t");
        assertThat(result.get(0)).containsEntry("avatarUrl", "");
        assertThat(result.get(0)).containsEntry("anonymous", true);
    }

    @Test
    void followingIdsMapsToStrings() {
        UUID uid = UUID.randomUUID();
        when(followRepository.findFollowedIdsByFollower(uid)).thenReturn(List.of(uid));

        var result = controller.followingIds(SECRET, uid);

        assertThat(result).containsExactly(uid.toString());
    }
}