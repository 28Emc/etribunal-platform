package com.etribunal.ai.automation.domain;

public class AiError extends RuntimeException {

    private final AiErrorCode code;
    private final boolean retryable;

    public AiError(AiErrorCode code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public AiErrorCode getCode() { return code; }
    public boolean isRetryable() { return retryable; }
}