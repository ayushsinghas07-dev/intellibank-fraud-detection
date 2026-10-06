package com.intellibank.exception;

import java.util.HashMap;
import java.util.Map;

public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Map<String, String> fieldErrors;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = new HashMap<>();
    }

    public BusinessException(String errorCode, String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
    }

    public String getErrorCode() { return errorCode; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
