package com.etribunal.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.etribunal.common.domain.exception.BadRequestException;
import com.etribunal.common.domain.exception.BusinessException;
import com.etribunal.common.domain.exception.ConflictException;
import com.etribunal.common.domain.exception.NotFoundException;
import com.etribunal.common.domain.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    org.springframework.validation.BindingResult bindingResult;

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationReturnsBadRequestWithErrors() {
        org.springframework.web.bind.MethodArgumentNotValidException ex = mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        lenient().when(bindingResult.getFieldErrors()).thenReturn(java.util.Collections.emptyList());

        ResponseEntity<?> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void handleUnauthorizedReturns401() {
        UnauthorizedException ex = new UnauthorizedException("Token inválido");
        ResponseEntity<?> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleConflictReturns409() {
        ConflictException ex = new ConflictException("username ya existe");
        ResponseEntity<?> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void handleNotFoundReturns404() {
        NotFoundException ex = new NotFoundException("User not found: 123");
        ResponseEntity<?> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void handleBadRequestReturns400() {
        BadRequestException ex = new BadRequestException("Dato inválido");
        ResponseEntity<?> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleBusinessReturns400() {
        BusinessException ex = new BusinessException("Regla de negocio");
        ResponseEntity<?> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void handleTypeMismatchReturns400() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                mock(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        doReturn(Integer.class).when(ex).getRequiredType();
        when(ex.getValue()).thenReturn("abc");

        ResponseEntity<?> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMissingParamReturns400() {
        org.springframework.web.bind.MissingServletRequestParameterException ex =
                new org.springframework.web.bind.MissingServletRequestParameterException("param", "String");
        ResponseEntity<?> response = handler.handleMissingParam(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleUnreadableReturns400() {
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                mock(org.springframework.http.converter.HttpMessageNotReadableException.class);
        ResponseEntity<?> response = handler.handleUnreadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGenericReturns500() {
        Exception ex = new RuntimeException("error interno");
        ResponseEntity<?> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}