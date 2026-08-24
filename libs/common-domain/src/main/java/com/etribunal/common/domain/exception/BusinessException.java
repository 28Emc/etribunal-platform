package com.etribunal.common.domain.exception;

/** Error de negocio genérico (mapear a HTTP 400/422 en la capa web). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
