package com.chris64233.cc.biobank.sample.dto;

import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record SampleResponse(
        Long id,
        String externalId,
        Long subjectId,
        Long consentVersionId,
        String sampleType,
        BigDecimal initialVolume,
        BigDecimal reservedVolume,
        BigDecimal remainingVolume,
        SampleStatus status,
        String storageLocation,
        Instant receivedAt,
        Instant frozenAt) {

    public static SampleResponse from(Sample sample) {
        return new SampleResponse(sample.getId(), sample.getExternalId(),
                sample.getSubjectId(), sample.getConsentVersionId(), sample.getSampleType(),
                sample.getInitialVolume(), sample.getReservedVolume(), sample.getRemainingVolume(),
                sample.getStatus(), sample.getStorageLocation(), sample.getReceivedAt(),
                sample.getFrozenAt());
    }
}
