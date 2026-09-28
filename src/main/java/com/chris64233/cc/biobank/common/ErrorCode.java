package com.chris64233.cc.biobank.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_EXTERNAL_ID(HttpStatus.CONFLICT),
    DUPLICATE_SUBJECT_CODE(HttpStatus.CONFLICT),
    DUPLICATE_CONSENT_VERSION(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY),
    /** 同意不支持该业务：不存在、已过期、已撤回或用途不匹配。 */
    CONSENT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    /** 对象当前状态不允许该操作（如冻结后领用、未冻结即处置、重复处置）。 */
    SAMPLE_NOT_DISPOSABLE(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
