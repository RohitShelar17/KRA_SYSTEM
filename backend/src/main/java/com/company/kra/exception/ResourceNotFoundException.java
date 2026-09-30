package com.company.kra.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + identifier);
    }
}
