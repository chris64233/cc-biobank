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

    @Column(name = "initial_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal initialVolume;

    @Column(name = "remaining_volume", nullable = false, precision = 19, scale = 3)
    private BigDecimal remainingVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AliquotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Aliquot() {
    }

    public Aliquot(Sample sample, BigDecimal volume) {
        this.sample = sample;
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

    public Long getId() {
        return id;
    }

    public Sample getSample() {
        return sample;
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
