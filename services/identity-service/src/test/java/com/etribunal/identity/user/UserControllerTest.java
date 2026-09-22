package com.etribunal.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.identity.api.ApiResponse;
import com.etribunal.identity.user.dto.DeleteAccountRequest;
import com.etribunal.identity.user.dto.UpdateProfileRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserService userService;

    UserController controller;

    UUID userId = UUID.randomUUID();
    AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
        principal = mock(AuthenticatedUser.class);
        lenient().when(principal.id()).thenReturn(userId);
    }

    @Test
    void searchWithoutPrincipalPassesNullRequester() {
        Map<String, Object> row = Map.of("username", "ana_t");
        when(userService.searchUsers("ana", null, 8, 0)).thenReturn(List.of(row));

        var response = controller.search("ana", 8, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((ApiResponse<List<Map<String, Object>>>) response.getBody()).data())
                .hasSize(1);
    }

    @Test
    void searchWithPrincipalPassesUserId() {
        when(userService.searchUsers("ana", userId, 8, 0)).thenReturn(List.of());

        controller.search("ana", 8, principal);

        verify(userService).searchUsers("ana", userId, 8, 0);
    }

    @Test
    void topJudgesWithPrincipal() {
        when(userService.topJudges(5, userId)).thenReturn(List.of());

        controller.topJudges(5, principal);

        verify(userService).topJudges(5, userId);
    }

    @Test
    void myProfileReturnsView() {
        when(userService.myProfile(userId)).thenReturn(Map.of("username", "ana_t"));

        var response = controller.myProfile(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(userService).myProfile(userId);
    }

    @Test
    void updateProfileDelegates() {
        UpdateProfileRequest request = new UpdateProfileRequest(null, null, "new_user", null, null);
        when(userService.updateProfile(userId, request)).thenReturn(Map.of());

        var response = controller.updateProfile(principal, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(userService).updateProfile(userId, request);
    }

    @Test
    void profilePathDelegates() {
        when(userService.profile("beto_j", null)).thenReturn(Map.of());

        controller.profile("beto_j", null);

        verify(userService).profile("beto_j", null);
    }

    @Test
    void toggleFollowDelegates() {
        when(userService.toggleFollow(userId, "beto_j")).thenReturn(Map.of("following", true));

        var response = controller.toggleFollow(principal, "beto_j");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((ApiResponse<Map<String, Object>>) response.getBody()).data())
                .containsEntry("following", true);
    }

    @Test
    void followersPathDelegates() {
        when(userService.followers("beto_j", null)).thenReturn(List.of());

        controller.followers("beto_j", null);

        verify(userService).followers("beto_j", null);
    }

    @Test
    void myFollowingDelegates() {
        when(userService.myFollowing(userId, 10, 20)).thenReturn(List.of());

        controller.myFollowing(principal, 10, 20);

        verify(userService).myFollowing(userId, 10, 20);
    }

    @Test
    void followingPathDelegates() {
        when(userService.following("beto_j", null)).thenReturn(List.of());

        controller.following("beto_j", null);

        verify(userService).following("beto_j", null);
    }

    @Test
    void deleteAccountDelegates() {
        DeleteAccountRequest request = new DeleteAccountRequest("beto_j");
        when(userService.softDelete(userId, "beto_j")).thenReturn(Map.of("success", true));

        var response = controller.deleteAccount(principal, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((ApiResponse<?>) response.getBody()).message()).isEqualTo("Cuenta eliminada");
        verify(userService).softDelete(userId, "beto_j");
    }
}