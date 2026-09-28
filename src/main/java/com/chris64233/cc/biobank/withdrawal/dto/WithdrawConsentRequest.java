package com.chris64233.cc.biobank.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawConsentRequest(
        @NotBlank(message = "撤回号不能为空") String withdrawalKey,
        /**
         * 指定撤回的同意版本号；为空时撤回受试者名下全部未撤回的同意版本。
         */
        String consentVersionCode,
        String reason) {
}
