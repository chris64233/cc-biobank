package com.chris64233.cc.biobank.withdrawal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 撤回结果：本次实际冻结的原始样本与在库分装清单就是冻结范围。
 * 已完成的历史领用不在其中，也不会被改写。
 */
public record WithdrawalResponse(
        String withdrawalKey,
        String subjectCode,
        List<String> withdrawnConsentVersions,
        List<FrozenSample> frozenSamples,
        List<FrozenAliquot> frozenAliquots,
        Instant withdrawnAt) {

    public record FrozenSample(
            Long sampleId,
            String externalId,
            BigDecimal remainingVolume) {
    }

    public record FrozenAliquot(
            Long aliquotId,
            Long sampleId,
            BigDecimal remainingVolume) {
    }
}
