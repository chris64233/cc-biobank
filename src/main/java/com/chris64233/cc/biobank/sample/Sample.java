package com.chris64233.cc.biobank.sample;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Sample() {
    }

    public Sample(String externalId, String sampleType, BigDecimal initialVolume,
            BigDecimal reservedVolume, String storageLocation, Instant receivedAt) {
        this.externalId = externalId;
        this.sampleType = sampleType;
        this.initialVolume = initialVolume;
        this.reservedVolume = reservedVolume;
        this.remainingVolume = initialVolume;
        this.storageLocation = storageLocation;
        this.receivedAt = receivedAt;
    }

    public void deduct(BigDecimal amount) {
        this.remainingVolume = this.remainingVolume.subtract(amount);
    }

    public Long getId() {
        return id;
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
}
