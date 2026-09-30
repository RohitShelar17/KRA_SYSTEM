package com.company.kra.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable codes. The frontend reacts to the code; the message
 * stays human enough to show directly to the user.
 */
public enum ErrorCode {

    VALIDATION_FAILED("Request validation failed", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("Requested resource was not found", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE("A record with these details already exists", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("Operation not allowed by a business rule", HttpStatus.UNPROCESSABLE_ENTITY),

    INVALID_CREDENTIALS("Email or password is incorrect", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("Account is locked after repeated failed sign-in attempts", HttpStatus.LOCKED),
    ACCOUNT_INACTIVE("Account is not active", HttpStatus.FORBIDDEN),
    TOKEN_EXPIRED("Session has expired, sign in again", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("Authentication token is not valid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED("Refresh token was already used; all sessions revoked", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    OUT_OF_SCOPE("This record is outside your access scope", HttpStatus.FORBIDDEN),
    RATE_LIMITED("Too many attempts from this address", HttpStatus.TOO_MANY_REQUESTS),

    EMPLOYEE_HAS_REPORTS("Employee still has direct reports assigned", HttpStatus.UNPROCESSABLE_ENTITY),
    CIRCULAR_REPORTING("Reporting relationship would create a cycle", HttpStatus.UNPROCESSABLE_ENTITY),
    DEPARTMENT_NOT_EMPTY("Department still has active employees", HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR("Something went wrong. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(String defaultMessage, HttpStatus status) {
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }
}
