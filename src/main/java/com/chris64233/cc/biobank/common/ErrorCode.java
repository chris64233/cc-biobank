package com.chris64233.cc.biobank.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_EXTERNAL_ID(HttpStatus.CONFLICT),
    DUPLICATE_CONSENT(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    SAMPLE_FROZEN(HttpStatus.CONFLICT),
    ALREADY_DISPOSED(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY),
    CONSENT_NOT_VALID(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
