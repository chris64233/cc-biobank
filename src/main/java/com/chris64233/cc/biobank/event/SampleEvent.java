package com.chris64233.cc.biobank.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 不可变业务事件，id 自增即事件顺序。
 */
@Entity
@Table(name = "sample_events")
public class SampleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "aliquot_id")
    private Long aliquotId;

    @Column(name = "volume_change", nullable = false, precision = 19, scale = 3)
    private BigDecimal volumeChange;

    @Column(name = "resulting_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal resultingVolume;

    @Column(name = "detail", length = 256)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SampleEvent() {
    }

    public SampleEvent(EventType eventType, Long sampleId, Long aliquotId,
            BigDecimal volumeChange, BigDecimal resultingVolume, String detail) {
        this.eventType = eventType;
        this.sampleId = sampleId;
        this.aliquotId = aliquotId;
        this.volumeChange = volumeChange;
        this.resultingVolume = resultingVolume;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public Long getAliquotId() {
        return aliquotId;
    }

    public BigDecimal getVolumeChange() {
        return volumeChange;
    }

    public BigDecimal getResultingVolume() {
        return resultingVolume;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
