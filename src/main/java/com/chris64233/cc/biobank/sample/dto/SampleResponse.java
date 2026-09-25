package com.chris64233.cc.biobank.sample.dto;

import com.chris64233.cc.biobank.sample.Sample;
import java.math.BigDecimal;
import java.time.Instant;

public record SampleResponse(
        Long id,
        String externalId,
        String sampleType,
        BigDecimal initialVolume,
        BigDecimal reservedVolume,
        BigDecimal remainingVolume,
        String storageLocation,
        Instant receivedAt) {

    public static SampleResponse from(Sample sample) {
        return new SampleResponse(sample.getId(), sample.getExternalId(), sample.getSampleType(),
                sample.getInitialVolume(), sample.getReservedVolume(), sample.getRemainingVolume(),
                sample.getStorageLocation(), sample.getReceivedAt());
    }
}
