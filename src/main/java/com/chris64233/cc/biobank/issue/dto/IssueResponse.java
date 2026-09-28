package com.chris64233.cc.biobank.issue.dto;

import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IssueResponse(
        String idempotencyKey,
        Long subjectId,
        String purpose,
        ConsentSnapshot consentSnapshot,
        List<Item> items,
        Instant issuedAt) {

    public record Item(
            Long aliquotId,
            BigDecimal issuedVolume,
            BigDecimal remainingVolume,
            AliquotStatus status) {
    }

    /**
     * 领用发生时刻实际采用的同意版本快照，随领用永久保存，不受日后撤回影响。
     */
    public record ConsentSnapshot(
            Long consentVersionId,
            String version,
            String purpose,
            List<String> allowedPurposes,
            Instant validFrom,
            Instant validUntil,
            Instant withdrawnAt,
            Instant snapshotAt) {
    }
}
