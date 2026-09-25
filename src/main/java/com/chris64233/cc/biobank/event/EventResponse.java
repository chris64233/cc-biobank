package com.chris64233.cc.biobank.event;

import java.math.BigDecimal;
import java.time.Instant;

public record EventResponse(
        Long id,
        EventType eventType,
        Long sampleId,
        Long aliquotId,
        BigDecimal volumeChange,
        BigDecimal resultingVolume,
        String detail,
        Instant occurredAt) {

    public static EventResponse from(SampleEvent event) {
        return new EventResponse(event.getId(), event.getEventType(), event.getSampleId(),
                event.getAliquotId(), event.getVolumeChange(), event.getResultingVolume(),
                event.getDetail(), event.getOccurredAt());
    }
}
