package com.company.kra.exception;

public class AccessScopeException extends BusinessException {

    public AccessScopeException(String message) {
        super(ErrorCode.OUT_OF_SCOPE, message);
    }
}
