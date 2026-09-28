package com.chris64233.cc.biobank.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

/**
 * 登记受试者同意书版本。
 * validFrom 缺省为当前时间；validUntil 缺省表示长期有效（直到撤回或新版本替代前仍有效）。
 */
public record RegisterConsentRequest(
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "同意版本不能为空") String version,
        @NotEmpty(message = "允许用途不能为空") List<@NotBlank(message = "用途不能为空") String> allowedPurposes,
        Instant validFrom,
        Instant validUntil) {
}
