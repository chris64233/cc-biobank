package com.chris64233.cc.biobank.aliquot.dto;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AliquotResponse(
        Long id,
        Long sampleId,
        Long subjectId,
        Long consentVersionId,
        BigDecimal initialVolume,
        BigDecimal remainingVolume,
        AliquotStatus status,
        Instant createdAt,
        Instant frozenAt) {

    public static AliquotResponse from(Aliquot aliquot) {
        return new AliquotResponse(aliquot.getId(), aliquot.getSample().getId(),
                aliquot.getSubjectId(), aliquot.getConsentVersionId(),
                aliquot.getInitialVolume(), aliquot.getRemainingVolume(),
                aliquot.getStatus(), aliquot.getCreatedAt(), aliquot.getFrozenAt());
    }
}
