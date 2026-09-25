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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 样本。接收产生的母样本 parentId 为空；分装产生的子样本 parentId 指向母样本。
 * currentVolume 与 reservedVolume 均为固定精度体积，所有比较使用 compareTo。
 */
@Entity
@Table(
        name = "sample",
        indexes = {
                @Index(name = "uk_sample_external_no", columnList = "externalSampleNo", unique = true),
                @Index(name = "idx_sample_parent", columnList = "parentId")
        }
)
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部唯一样本号，全局唯一。 */
    @Column(nullable = false, updatable = false, length = 128)
    private String externalSampleNo;

    @Column(nullable = false, length = 64)
    private String sampleType;

    @Column(nullable = false, precision = 28, scale = Volumes.SCALE)
    private BigDecimal initialVolume;

    @Column(nullable = false, precision = 28, scale = Volumes.SCALE)
    private BigDecimal currentVolume;

    /** 不可使用的保留体积，子样本默认为 0。 */
    @Column(nullable = false, precision = 28, scale = Volumes.SCALE)
    private BigDecimal reservedVolume;

    @Column(nullable = false, length = 256)
    private String storageLocation;

    @Column(nullable = false, updatable = false)
    private java.time.Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SampleStatus status = SampleStatus.ACTIVE;

    /** 母样本 ID；母样本为 null。 */
    private Long parentId;

    /** 乐观锁版本，防止丢失更新（悲观锁之外的第二道防线）。 */
    @Version
    private Long version;

    protected Sample() {
    }

    public Sample(String externalSampleNo, String sampleType, BigDecimal initialVolume,
                  BigDecimal reservedVolume, String storageLocation,
                  java.time.Instant receivedAt, Long parentId) {
        this.externalSampleNo = externalSampleNo;
        this.sampleType = sampleType;
        this.initialVolume = Volumes.normalize(initialVolume);
        this.currentVolume = this.initialVolume;
        this.reservedVolume = Volumes.normalize(reservedVolume);
        this.storageLocation = storageLocation;
        this.receivedAt = receivedAt;
        this.parentId = parentId;
    }

    /** 当前可使用体积（当前量 - 保留量）。 */
    public BigDecimal availableVolume() {
        return currentVolume.subtract(reservedVolume);
    }

    public void deductAvailable(BigDecimal amount) {
        BigDecimal deducted = Volumes.normalize(amount);
        if (deducted.compareTo(availableVolume()) > 0) {
            throw new IllegalStateException("insufficient available volume for sample " + id);
        }
        currentVolume = currentVolume.subtract(deducted);
        if (currentVolume.compareTo(reservedVolume) == 0) {
            status = SampleStatus.EXHAUSTED;
        }
    }

    public Long getId() {
        return id;
    }

    public String getExternalSampleNo() {
        return externalSampleNo;
    }

    public String getSampleType() {
        return sampleType;
    }

    public BigDecimal getInitialVolume() {
        return initialVolume;
    }

    public BigDecimal getCurrentVolume() {
        return currentVolume;
    }

    public BigDecimal getReservedVolume() {
        return reservedVolume;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public java.time.Instant getReceivedAt() {
        return receivedAt;
    }

    public SampleStatus getStatus() {
        return status;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getVersion() {
        return version;
    }
}
