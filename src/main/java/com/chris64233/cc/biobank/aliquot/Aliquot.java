package com.chris64233.cc.biobank.aliquot;

import com.chris64233.cc.biobank.sample.Sample;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "aliquots")
public class Aliquot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false)
    private Sample sample;

    /** 冗余受试者 id，与母样本一致，便于按受试者冻结与查询。 */
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** 分装时继承的同意版本快照（consent_versions.id）。 */
    @Column(name = "consent_version_id", nullable = false)
    private Long consentVersionId;

    @Column(name = "initial_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal initialVolume;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AliquotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    protected Aliquot() {
    }

    public Aliquot(Sample sample, BigDecimal volume) {
        this.sample = sample;
        this.subjectId = sample.getSubjectId();
        this.consentVersionId = sample.getConsentVersionId();
        this.initialVolume = volume;
        this.remainingVolume = volume;
        this.status = AliquotStatus.AVAILABLE;
        this.createdAt = Instant.now();
    }

    public void deduct(BigDecimal amount) {
        this.remainingVolume = this.remainingVolume.subtract(amount);
        if (this.remainingVolume.signum() == 0) {
            this.status = AliquotStatus.DEPLETED;
        }
    }

    /** 撤回冻结：仍在库可用的分装被冻结；已耗尽的分装保持耗尽状态。 */
    public void freeze(Instant at) {
        if (this.status == AliquotStatus.AVAILABLE) {
            this.status = AliquotStatus.FROZEN;
            this.frozenAt = at;
        }
    }

    /**
     * 处置冻结分装。仅允许从 FROZEN 转入终态；DESTROY/RETURN 将在库剩余量清零，
     * RETAIN 保留体积但禁止研究使用。
     */
    public void dispose(AliquotStatus target, boolean clearVolume) {
        this.status = target;
        if (clearVolume) {
            this.remainingVolume = BigDecimal.ZERO;
        }
    }

    public Long getId() {
        return id;
    }

    public Sample getSample() {
        return sample;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getConsentVersionId() {
        return consentVersionId;
    }

    public BigDecimal getInitialVolume() {
        return initialVolume;
    }

    public BigDecimal getRemainingVolume() {
        return remainingVolume;
    }

    public AliquotStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }
}
