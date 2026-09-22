package com.etribunal.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.identity.auth.dto.ChangePasswordRequest;
import com.etribunal.identity.auth.dto.ForgotPasswordRequest;
import com.etribunal.identity.auth.dto.LoginRequest;
import com.etribunal.identity.auth.dto.RefreshRequest;
import com.etribunal.identity.auth.dto.RegisterRequest;
import com.etribunal.identity.auth.dto.ResetPasswordRequest;
import com.etribunal.identity.auth.dto.TokenResponse;
import com.etribunal.identity.auth.dto.UserResponse;
import com.etribunal.identity.auth.dto.VerifyEmailRequest;
import com.etribunal.identity.auth.dto.ResendVerificationRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void registerReturnsCreatedWithTokens() {
        RegisterRequest request = new RegisterRequest("test@test.com", "user_t", "Password1", "Test");
        TokenResponse mockResponse = mock(TokenResponse.class);
        when(authService.register(any())).thenReturn(mockResponse);

        var response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(mockResponse);
        verify(authService).register(any());
    }

    @Test
    void loginReturnsOkWithTokens() {
        LoginRequest request = new LoginRequest("user", "Password1");
        TokenResponse mockResponse = mock(TokenResponse.class);
        when(authService.login(any())).thenReturn(mockResponse);

        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(mockResponse);
        verify(authService).login(any());
    }

    @Test
    void refreshReturnsOkWithTokens() {
        RefreshRequest request = new RefreshRequest("refresh-token");
        TokenResponse mockResponse = mock(TokenResponse.class);
        when(authService.refresh(any())).thenReturn(mockResponse);

        var response = controller.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(mockResponse);
        verify(authService).refresh(any());
    }

    @Test
    void logoutReturnsNoContent() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        lenient().when(user.id()).thenReturn(UUID.randomUUID());

        var response = controller.logout(user);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).logout(user.id());
    }

    @Test
    void meReturnsUserResponse() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        UUID userId = UUID.randomUUID();
        lenient().when(user.id()).thenReturn(userId);
        UserResponse mockResponse = mock(UserResponse.class);
        when(authService.me(userId)).thenReturn(mockResponse);

        var response = controller.me(user);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).me(userId);
    }

    @Test
    void changePasswordReturnsNoContent() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        UUID userId = UUID.randomUUID();
        lenient().when(user.id()).thenReturn(userId);
        com.etribunal.identity.auth.dto.ChangePasswordRequest request = new com.etribunal.identity.auth.dto.ChangePasswordRequest("old", "new");

        var response = controller.changePassword(user, request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).changePassword(userId, request);
    }

    @Test
    void forgotPasswordReturnsNoContent() {
        com.etribunal.identity.auth.dto.ForgotPasswordRequest request = new com.etribunal.identity.auth.dto.ForgotPasswordRequest("test@test.com");
        var response = controller.forgotPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).forgotPassword(request);
    }

    @Test
    void resetPasswordReturnsNoContent() {
        com.etribunal.identity.auth.dto.ResetPasswordRequest request = new com.etribunal.identity.auth.dto.ResetPasswordRequest("token", "NewPass1");
        var response = controller.resetPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).resetPassword(request);
    }

    @Test
    void verifyEmailReturnsNoContent() {
        com.etribunal.identity.auth.dto.VerifyEmailRequest request = new com.etribunal.identity.auth.dto.VerifyEmailRequest("token");
        var response = controller.verifyEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).verifyEmail(request.token());
    }

    @Test
    void resendVerificationReturnsNoContent() {
        com.etribunal.identity.auth.dto.ResendVerificationRequest request = new com.etribunal.identity.auth.dto.ResendVerificationRequest("test@test.com");
        var response = controller.resendVerification(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).resendVerificationEmail(request.email());
    }

    @Test
    void checkExistenceReturnsNoContent() {
        var response = controller.checkExistence("email", "username");

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(authService).checkExistence("email", "username");
    }
}