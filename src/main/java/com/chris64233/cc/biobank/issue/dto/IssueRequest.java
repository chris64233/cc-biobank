package com.chris64233.cc.biobank.issue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record IssueRequest(
        @NotBlank(message = "幂等键不能为空") String idempotencyKey,
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "同意版本号不能为空") String consentVersionCode,
        @NotBlank(message = "领用用途不能为空") String purpose,
        @NotEmpty(message = "领用明细不能为空") List<@Valid Item> items) {

    public record Item(
            @jakarta.validation.constraints.NotNull(message = "子样本 id 不能为空") Long aliquotId,
            @jakarta.validation.constraints.NotNull(message = "领用体积不能为空")
            BigDecimal volume) {
    }
}
