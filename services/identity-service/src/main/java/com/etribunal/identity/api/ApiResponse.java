package com.etribunal.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message, int statusCode) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, 200);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(data, message, 200);
    }

    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return new ApiResponse<>(null, message, statusCode);
    }
}
