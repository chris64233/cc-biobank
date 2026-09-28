package com.chris64233.cc.biobank.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 受试者同意撤回。
 * withdrawalNo 为业务撤回号，必须幂等：同号重放返回首次结果，不重复冻结。
 */
public record WithdrawConsentRequest(
        @NotBlank(message = "撤回号不能为空") String withdrawalNo,
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        String reason) {
}
