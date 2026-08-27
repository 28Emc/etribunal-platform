package com.etribunal.identity.user;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.identity.api.ApiResponse;
import com.etribunal.identity.user.dto.DeleteAccountRequest;
import com.etribunal.identity.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> search(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "take", defaultValue = "8") int take,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.searchUsers(q, userIdOrNull(principal), take)));
    }

    @GetMapping("/top-judges")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> topJudges(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.topJudges(limit, userIdOrNull(principal))));
    }

    @GetMapping("/profile/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myProfile(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.myProfile(principal.id())));
    }

    @PatchMapping("/profile/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(principal.id(), request)));
    }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profile(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.profile(username, userIdOrNull(principal))));
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFollow(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String username) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.toggleFollow(principal.id(), username)));
    }

    @GetMapping("/{username}/followers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> followers(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.followers(username, userIdOrNull(principal))));
    }

    @GetMapping("/me/following")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myFollowing(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "skip", defaultValue = "0") int skip,
            @RequestParam(name = "take", defaultValue = "20") int take) {
        return ResponseEntity.ok(ApiResponse.ok(userService.myFollowing(principal.id(), skip, take)));
    }

    @GetMapping("/{username}/following")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> following(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.following(username, userIdOrNull(principal))));
    }

    @DeleteMapping("/account/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeleteAccountRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.softDelete(principal.id(), request.username()), "Cuenta eliminada"));
    }

    private static UUID userIdOrNull(AuthenticatedUser principal) {
        return Optional.ofNullable(principal).map(AuthenticatedUser::id).orElse(null);
    }
}
