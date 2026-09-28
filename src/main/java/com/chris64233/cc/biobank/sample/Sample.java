package com.chris64233.cc.biobank.sample;

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

@Entity
@Table(name = "samples")
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** 接收时采用的同意版本快照（consent_versions.id）。 */
    @Column(name = "consent_version_id", nullable = false)
    private Long consentVersionId;

    @Column(name = "sample_type", nullable = false, length = 64)
    private String sampleType;

    @Column(name = "initial_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal initialVolume;

    @Column(name = "reserved_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal reservedVolume;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SampleStatus status;

    @Column(name = "storage_location", nullable = false, length = 128)
    private String storageLocation;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    protected Sample() {
    }

    public Sample(String externalId, Long subjectId, Long consentVersionId, String sampleType,
            BigDecimal initialVolume, BigDecimal reservedVolume, String storageLocation,
            Instant receivedAt) {
        this.externalId = externalId;
        this.subjectId = subjectId;
        this.consentVersionId = consentVersionId;
        this.sampleType = sampleType;
        this.initialVolume = initialVolume;
        this.reservedVolume = reservedVolume;
        this.remainingVolume = initialVolume;
        this.storageLocation = storageLocation;
        this.receivedAt = receivedAt;
        this.status = SampleStatus.AVAILABLE;
    }

    public void deduct(BigDecimal amount) {
        this.remainingVolume = this.remainingVolume.subtract(amount);
    }

    public void freeze(Instant at) {
        if (this.status == SampleStatus.AVAILABLE) {
            this.status = SampleStatus.FROZEN;
            this.frozenAt = at;
        }
    }

    /**
     * 处置冻结样本。仅允许从 FROZEN 转入终态；DESTROY/RETURN 将在库剩余量清零，
     * RETAIN 保留体积但禁止研究使用。
     */
    public void dispose(SampleStatus target, boolean clearVolume) {
        this.status = target;
        if (clearVolume) {
            this.remainingVolume = BigDecimal.ZERO;
        }
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getConsentVersionId() {
        return consentVersionId;
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

    public SampleStatus getStatus() {
        return status;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }
}
