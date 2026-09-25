package com.chris64233.cc.biobank.issue.dto;

import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IssueResponse(
        String idempotencyKey,
        List<Item> items,
        Instant issuedAt) {

    public record Item(
            Long aliquotId,
            BigDecimal issuedVolume,
            BigDecimal remainingVolume,
            AliquotStatus status) {
    }
}
