package com.etribunal.common.domain.exception;

/** Conflicto de estado (HTTP 409): duplicados, transiciones inválidas, etc. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
