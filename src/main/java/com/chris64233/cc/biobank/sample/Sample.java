package com.chris64233.cc.biobank.sample;

import com.chris64233.cc.biobank.subject.Subject;
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
@Table(name = "samples")
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** 接收样本时实际采用的同意版本号快照。 */
    @Column(name = "consent_version_code", nullable = false, length = 64)
    private String consentVersionCode;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(name = "sample_type", nullable = false, length = 64)
    private String sampleType;

    @Column(name = "initial_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal initialVolume;

    @Column(name = "reserved_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal reservedVolume;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Column(name = "storage_location", nullable = false, length = 128)
    private String storageLocation;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SampleStatus status;

    protected Sample() {
    }

    public Sample(Subject subject, String consentVersionCode, String externalId, String sampleType,
            BigDecimal initialVolume, BigDecimal reservedVolume, String storageLocation,
            Instant receivedAt) {
        this.subject = subject;
        this.consentVersionCode = consentVersionCode;
        this.externalId = externalId;
        this.sampleType = sampleType;
        this.initialVolume = initialVolume;
        this.reservedVolume = reservedVolume;
        this.remainingVolume = initialVolume;
        this.storageLocation = storageLocation;
        this.receivedAt = receivedAt;
        this.status = SampleStatus.ACTIVE;
    }

    public void deduct(BigDecimal amount) {
        this.remainingVolume = this.remainingVolume.subtract(amount);
    }

    /** 撤回冻结：原始样本停止后续分装，但已完成的分装与领用不变。 */
    public void freeze() {
        this.status = SampleStatus.FROZEN;
    }

    /** 处置终态；销毁/返还清零体积台账，保留禁用研究保留实物与体积。 */
    public void dispose(SampleStatus target) {
        if (this.status != SampleStatus.FROZEN) {
            throw new IllegalStateException("只能处置已冻结的原始样本: #" + id);
        }
        this.status = target;
        if (target == SampleStatus.DESTROYED || target == SampleStatus.RETURNED) {
            this.remainingVolume = java.math.BigDecimal.ZERO;
        }
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getConsentVersionCode() {
        return consentVersionCode;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getSampleType() {
        return sampleType;
    }

    public BigDecimal getInitialVolume() {
        return initialVolume;
    }

    public BigDecimal getReservedVolume() {
        return reservedVolume;
    }

    public BigDecimal getRemainingVolume() {
        return remainingVolume;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public SampleStatus getStatus() {
        return status;
    }
}
