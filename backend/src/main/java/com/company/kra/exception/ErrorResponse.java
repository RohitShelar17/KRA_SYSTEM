package com.company.kra.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String code,
                            String message, String path, String traceId, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
