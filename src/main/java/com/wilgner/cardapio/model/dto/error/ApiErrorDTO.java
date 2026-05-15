package com.wilgner.cardapio.model.dto.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorDTO(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiErrorDTO of(int status, String error, String message, String path) {
        return new ApiErrorDTO(Instant.now(), status, error, message, path, Map.of());
    }

    public static ApiErrorDTO validation(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ApiErrorDTO(Instant.now(), status, error, message, path, fieldErrors);
    }
}
