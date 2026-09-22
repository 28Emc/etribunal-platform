package com.etribunal.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.identity.api.ApiResponse;
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
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) response.getBody()).data()).isSameAs(mockResponse);
        verify(authService).register(any());
    }

    @Test
    void loginReturnsOkWithTokens() {
        LoginRequest request = new LoginRequest("user", "Password1");
        TokenResponse mockResponse = mock(TokenResponse.class);
        when(authService.login(any())).thenReturn(mockResponse);

        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) response.getBody()).data()).isSameAs(mockResponse);
        verify(authService).login(any());
    }

    @Test
    void refreshReturnsOkWithTokens() {
        RefreshRequest request = new RefreshRequest("refresh-token");
        TokenResponse mockResponse = mock(TokenResponse.class);
        when(authService.refresh(any())).thenReturn(mockResponse);

        var response = controller.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) response.getBody()).data()).isSameAs(mockResponse);
        verify(authService).refresh(any());
    }

    @Test
    void logoutReturnsOk() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        lenient().when(user.id()).thenReturn(UUID.randomUUID());

        var response = controller.logout(user);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
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

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) response.getBody()).data()).isSameAs(mockResponse);
        verify(authService).me(userId);
    }

@Test
    void changePasswordReturnsOk() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        UUID userId = UUID.randomUUID();
        lenient().when(user.id()).thenReturn(userId);
        ChangePasswordRequest request = new ChangePasswordRequest("old", "new");

        var response = controller.changePassword(user, request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).changePassword(userId, request);
    }

    @Test
    void forgotPasswordReturnsOk() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@test.com");
        var response = controller.forgotPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).forgotPassword(request);
    }

    @Test
    void resetPasswordReturnsOk() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "NewPass1");
        var response = controller.resetPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).resetPassword(request);
    }

    @Test
    void verifyEmailReturnsOk() {
        VerifyEmailRequest request = new VerifyEmailRequest("token");
        var response = controller.verifyEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).verifyEmail(request.token());
    }

    @Test
    void resendVerificationReturnsOk() {
        ResendVerificationRequest request = new ResendVerificationRequest("test@test.com");
        var response = controller.resendVerification(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).resendVerificationEmail(request.email());
    }

    @Test
    void checkExistenceReturnsOk() {
        var response = controller.checkExistence("email", "username");

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(authService).checkExistence("email", "username");
    }
}