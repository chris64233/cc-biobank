package com.chris64233.cc.biobank.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 不可变业务事件：每次接收/分装/领用各写入一条或多条事件。
 * 只追加（insert-only），不提供更新/删除路径。
 */
@Entity
@Table(
        name = "sample_event",
        indexes = {
                @Index(name = "idx_event_sample_seq", columnList = "sampleId,sequence", unique = true),
                @Index(name = "idx_event_group", columnList = "eventGroupId")
        }
)
public class SampleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private EventType eventType;

    @Column(nullable = false, updatable = false)
    private Long sampleId;

    /** 样本内事件顺序，从 1 开始。 */
    @Column(nullable = false, updatable = false)
    private long sequence;

    /** 同一次分装/领用的全部事件共享同一分组号。 */
    @Column(nullable = false, updatable = false)
    private long eventGroupId;

    /** 本次变动体积（正数）：分装为扣减量，子样本接收为其创建量。 */
    @Column(nullable = false, updatable = false, precision = 28, scale = Volumes.SCALE)
    private BigDecimal volumeDelta;

    /** 事件后当前体积。 */
    @Column(nullable = false, updatable = false, precision = 28, scale = Volumes.SCALE)
    private BigDecimal resultingVolume;

    /** 关联样本（如子样本、领用行样本），可空。 */
    private Long relatedSampleId;

    /** 领用幂等键，可空。 */
    @Column(updatable = false, length = 128)
    private String idempotencyKey;

    @Lob
    @Column(updatable = false)
    private String detailJson;

    @Column(nullable = false, updatable = false)
    private java.time.Instant occurredAt;

    protected SampleEvent() {
    }

    public SampleEvent(EventType eventType, Long sampleId, long sequence, long eventGroupId,
                       BigDecimal volumeDelta, BigDecimal resultingVolume, Long relatedSampleId,
                       String idempotencyKey, String detailJson, java.time.Instant occurredAt) {
        this.eventType = eventType;
        this.sampleId = sampleId;
        this.sequence = sequence;
        this.eventGroupId = eventGroupId;
        this.volumeDelta = Volumes.normalize(volumeDelta);
        this.resultingVolume = Volumes.normalize(resultingVolume);
        this.relatedSampleId = relatedSampleId;
        this.idempotencyKey = idempotencyKey;
        this.detailJson = detailJson;
        this.occurredAt = occurredAt;
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

    public long getSequence() {
        return sequence;
    }

    public long getEventGroupId() {
        return eventGroupId;
    }

    public BigDecimal getVolumeDelta() {
        return volumeDelta;
    }

    public BigDecimal getResultingVolume() {
        return resultingVolume;
    }

    public Long getRelatedSampleId() {
        return relatedSampleId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public java.time.Instant getOccurredAt() {
        return occurredAt;
    }
}
