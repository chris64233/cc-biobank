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

    /** 分装时从母样本继承的同意版本号快照。 */
    @Column(name = "consent_version_code", nullable = false, length = 64)
    private String consentVersionCode;

    @Column(name = "initial_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal initialVolume;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AliquotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Aliquot() {
    }

    public Aliquot(Sample sample, BigDecimal volume) {
        this.sample = sample;
        this.consentVersionCode = sample.getConsentVersionCode();
        this.initialVolume = volume;
        this.remainingVolume = volume;
        this.status = AliquotStatus.AVAILABLE;
        this.createdAt = Instant.now();
    }

    /** 领用扣减；扣完进入 DEPLETED，历史领用记录不被撤回改写。 */
    public void deduct(BigDecimal amount) {
        this.remainingVolume = this.remainingVolume.subtract(amount);
        if (this.remainingVolume.signum() == 0) {
            this.status = AliquotStatus.DEPLETED;
        }
    }

    /** 撤回冻结：仅在库可用样本会被冻结。 */
    public void freeze() {
        if (this.status != AliquotStatus.AVAILABLE) {
            throw new IllegalStateException("只能冻结在库可用的分装: #" + id);
        }
        this.status = AliquotStatus.FROZEN;
    }

    /** 处置终态；销毁/返还清零体积台账，保留禁用研究保留实物与体积。 */
    public void dispose(AliquotStatus target) {
        if (this.status != AliquotStatus.FROZEN) {
            throw new IllegalStateException("只能处置已冻结的分装: #" + id);
        }
        this.status = target;
        if (target == AliquotStatus.DESTROYED || target == AliquotStatus.RETURNED) {
            this.remainingVolume = BigDecimal.ZERO;
        }
    }

    public Long getId() {
        return id;
    }

    public Sample getSample() {
        return sample;
    }

    public String getConsentVersionCode() {
        return consentVersionCode;
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
}
