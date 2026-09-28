package com.chris64233.cc.biobank.issue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 领用请求。
 * subjectCode 为受试者；purpose 为本次用途；consentVersion 为本次实际采用的同意版本，
 * 领用发生时必须处于有效期、未撤回且允许该用途。一次领用只能涉及同一受试者的分装。
 */
public record IssueRequest(
        @NotBlank(message = "幂等键不能为空") String idempotencyKey,
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "领用用途不能为空") String purpose,
        @NotBlank(message = "同意版本不能为空") String consentVersion,
        @NotEmpty(message = "领用明细不能为空") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "子样本 id 不能为空") Long aliquotId,
            @NotNull(message = "领用体积不能为空") BigDecimal volume) {
    }
}
