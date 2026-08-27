package com.etribunal.common.domain.exception;

/** Credenciales/token inválidos o sesión revocada (HTTP 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
