package com.company.kra.common.dto;

import java.time.LocalDateTime;

/**
 * Envelope for successful responses. Errors use ErrorResponse instead.
 */
public record ApiResponse<T>(boolean success, String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, null, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, LocalDateTime.now());
    }
}
