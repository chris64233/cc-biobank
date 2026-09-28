package com.chris64233.cc.biobank.disposal;

import com.chris64233.cc.biobank.aliquot.AliquotStatus;
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
 * 处置明细：每个被处置的原始样本/分装一条，支撑受试者维度的处置进度查询。
 */
@Entity
@Table(name = "disposal_items")
public class DisposalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposal_key", nullable = false, length = 128)
    private String disposalKey;

    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private DisposalTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 24)
    private DisposalDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, length = 24)
    private AliquotStatus resultingStatus;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Column(name = "disposed_at", nullable = false)
    private Instant disposedAt;

    protected DisposalItem() {
    }

    public DisposalItem(String disposalKey, String subjectCode, DisposalTargetType targetType,
            Long targetId, DisposalDecision decision, AliquotStatus resultingStatus,
            BigDecimal remainingVolume, Instant disposedAt) {
        this.disposalKey = disposalKey;
        this.subjectCode = subjectCode;
        this.targetType = targetType;
        this.targetId = targetId;
        this.decision = decision;
        this.resultingStatus = resultingStatus;
        this.remainingVolume = remainingVolume;
        this.disposedAt = disposedAt;
    }

    public Long getId() {
        return id;
    }

    public String getDisposalKey() {
        return disposalKey;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public DisposalTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public DisposalDecision getDecision() {
        return decision;
    }

    public AliquotStatus getResultingStatus() {
        return resultingStatus;
    }

    public BigDecimal getRemainingVolume() {
        return remainingVolume;
    }

    public Instant getDisposedAt() {
        return disposedAt;
    }
}
