package com.chris64233.cc.biobank.aliquot.dto;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AliquotResponse(
        Long id,
        Long sampleId,
        String subjectCode,
        String consentVersionCode,
        BigDecimal initialVolume,
        BigDecimal remainingVolume,
        AliquotStatus status,
        Instant createdAt) {

    public static AliquotResponse from(Aliquot aliquot) {
        return new AliquotResponse(aliquot.getId(), aliquot.getSample().getId(),
                aliquot.getSample().getSubject().getSubjectCode(),
                aliquot.getConsentVersionCode(),
                aliquot.getInitialVolume(), aliquot.getRemainingVolume(),
                aliquot.getStatus(), aliquot.getCreatedAt());
    }
}
