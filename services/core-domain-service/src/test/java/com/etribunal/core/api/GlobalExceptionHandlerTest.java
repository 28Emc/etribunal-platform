package com.etribunal.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MissingRequestHeaderException missingHeader;

    @Test
    void handleValidationJoinsFieldErrors() throws Exception {
        Method m = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        binding.addError(new FieldError("target", "title", "no puede estar vacío"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(m, 0), binding);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("title: no puede estar vacío");
    }

    @Test
    void handleUnreadableReturnsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnreadable(new HttpMessageNotReadableException("cuerpo malformado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Cuerpo de la petición inválido");
    }

    @Test
    void handleTypeMismatchIncludesParameterName() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Parámetro inválido: page");
    }

    @Test
    void handleMissingParamIncludesParameterName() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParam(
                new MissingServletRequestParameterException("take", "int"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Falta el parámetro: take");
    }

    @Test
    void handleMissingHeaderIncludesHeaderName() {
        when(missingHeader.getHeaderName()).thenReturn("X-User-Id");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingHeader(missingHeader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Falta el header: X-User-Id");
    }

    @Test
    void handleDataIntegrityReturnsConflict() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("restricción violada"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleResponseStatusUsesReason() {
        var ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Acceso denegado");
    }

    @Test
    void handleResponseStatusWithoutReasonUsesDefaultPhrase() {
        var response =
                handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo(HttpStatus.NOT_FOUND.getReasonPhrase());
    }

    @Test
    void handleNoResourceReturnsNotFound() {
        var response =
                handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/api/cases"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleIllegalStateReturnsUnauthorized() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalState(new IllegalStateException("Usuario no autenticado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Usuario no autenticado");
    }

    @Test
    void handleGenericReturnsInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
        assertThat(value).isNotBlank();
    }
}