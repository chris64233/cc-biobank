package com.chris64233.cc.biobank.disposal.dto;

import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.disposal.DisposalDecision;
import com.chris64233.cc.biobank.disposal.DisposalTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DisposalResponse(
        String disposalKey,
        String subjectCode,
        DisposalDecision decision,
        List<Item> items,
        Instant disposedAt) {

    public record Item(
            DisposalTargetType targetType,
            Long targetId,
            BigDecimal volumeBefore,
            BigDecimal remainingVolume,
            AliquotStatus resultingStatus) {
    }
}
