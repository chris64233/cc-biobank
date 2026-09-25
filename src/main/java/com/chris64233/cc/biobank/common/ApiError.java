package com.chris64233.cc.biobank.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> details) {

    public static ApiError of(ErrorCode code, String message, Map<String, String> details) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message,
                details == null || details.isEmpty() ? null : details);
    }
}
