package com.chris64233.cc.biobank.withdrawal.dto;

import java.time.Instant;
import java.util.List;

/**
 * 撤回结果即冻结范围。
 * frozenSampleIds/frozenAliquotIds 为本次实际转为 FROZEN 的在库样本与分装；
 * 已耗尽、已处置的不在列表中；历史领用不受影响。
 */
public record WithdrawalResponse(
        String withdrawalNo,
        Long subjectId,
        List<Long> consentVersionIds,
        List<Long> frozenSampleIds,
        List<Long> frozenAliquotIds,
        Instant withdrawnAt) {
}
