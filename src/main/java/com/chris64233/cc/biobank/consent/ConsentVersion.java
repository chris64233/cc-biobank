package com.chris64233.cc.biobank.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 受试者同意书的一个版本。
 *
 * <p>同一受试者 + version 唯一。allowedPurposes 为逗号分隔的用途清单。
 * validFrom/validUntil 界定有效期；withdrawnAt 非空表示已撤回。撤回不删除记录，
 * 历史领用上保存的同意快照不受影响。
 */
@Entity
@Table(name = "consent_versions", uniqueConstraints =
        @UniqueConstraint(name = "uk_consent_subject_version",
                columnNames = {"subject_id", "version"}))
public class ConsentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "allowed_purposes", nullable = false, length = 512)
    private String allowedPurposes;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsentVersion() {
    }

    public ConsentVersion(Subject subject, String version, String allowedPurposes,
            Instant validFrom, Instant validUntil) {
        this.subject = subject;
        this.version = version;
        this.allowedPurposes = allowedPurposes;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.createdAt = Instant.now();
    }

    public void withdraw(Instant at) {
        if (this.withdrawnAt == null) {
            this.withdrawnAt = at;
        }
    }

    /**
     * at 时刻该同意是否仍支持指定用途：在有效期内、未撤回且用途在允许清单中。
     */
    public boolean supports(String purpose, Instant at) {
        if (withdrawnAt != null && !withdrawnAt.isAfter(at)) {
            return false;
        }
        if (validFrom.isAfter(at)) {
            return false;
        }
        if (validUntil != null && validUntil.isBefore(at)) {
            return false;
        }
        for (String allowed : allowedPurposes.split(",")) {
            if (allowed.trim().equals(purpose)) {
                return true;
            }
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getVersion() {
        return version;
    }

    public String getAllowedPurposes() {
        return allowedPurposes;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
