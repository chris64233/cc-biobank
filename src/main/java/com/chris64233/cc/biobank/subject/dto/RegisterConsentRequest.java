package com.chris64233.cc.biobank.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.Set;

/**
 * 登记同意版本。validUntil 为空表示长期有效（仍可被撤回）；
 * allowedPurposes 为允许的研究用途代码集合，必须至少包含一个用途。
 */
public record RegisterConsentRequest(
        @NotBlank(message = "同意版本号不能为空") String versionCode,
        @NotEmpty(message = "同意用途不能为空") Set<String> allowedPurposes,
        Instant validFrom,
        Instant validUntil) {
}
