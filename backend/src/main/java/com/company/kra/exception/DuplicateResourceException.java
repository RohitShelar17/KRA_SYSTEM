package com.company.kra.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(ErrorCode.DUPLICATE_RESOURCE, resource + " already exists with " + field + " " + value);
    }
}
