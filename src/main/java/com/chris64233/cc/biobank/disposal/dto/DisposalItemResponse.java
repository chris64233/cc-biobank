package com.chris64233.cc.biobank.disposal.dto;

import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.disposal.DisposalDecision;
import com.chris64233.cc.biobank.disposal.DisposalItem;
import com.chris64233.cc.biobank.disposal.DisposalTargetType;
import java.math.BigDecimal;
import java.time.Instant;

public record DisposalItemResponse(
        String disposalKey,
        DisposalTargetType targetType,
        Long targetId,
        DisposalDecision decision,
        AliquotStatus resultingStatus,
        BigDecimal remainingVolume,
        Instant disposedAt) {

    public static DisposalItemResponse from(DisposalItem item) {
        return new DisposalItemResponse(item.getDisposalKey(), item.getTargetType(),
                item.getTargetId(), item.getDecision(), item.getResultingStatus(),
                item.getRemainingVolume(), item.getDisposedAt());
    }
}
