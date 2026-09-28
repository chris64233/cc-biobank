package com.chris64233.cc.biobank.subject;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.HashSet;
import java.util.Set;

/**
 * 受试者的一个同意版本。撤回后 withdrawnAt 被置位，历史领用仍引用其不可变快照。
 */
@Entity
@Table(name = "consents", uniqueConstraints =
        @UniqueConstraint(name = "uk_consent_subject_version",
                columnNames = {"subject_id", "version_code"}))
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** 业务可见的同意版本号，同一受试者内唯一，如 v1、2026.1。 */
    @Column(name = "version_code", nullable = false, length = 64)
    private String versionCode;

    /** 允许的研究用途代码集合，为空集合表示不允许任何研究领用。 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "consent_purposes",
            joinColumns = @JoinColumn(name = "consent_id", nullable = false))
    @Column(name = "purpose", nullable = false, length = 64)
    private Set<String> allowedPurposes = new HashSet<>();

    /** 用途开放的起始时间（含）。 */
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** 用途开放的截止时间（不含），null 表示长期有效（仍可被撤回）。 */
    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Consent() {
    }

    public Consent(Subject subject, String versionCode, Set<String> allowedPurposes,
            Instant validFrom, Instant validUntil) {
        this.subject = subject;
        this.versionCode = versionCode;
        this.allowedPurposes = new HashSet<>(allowedPurposes);
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.createdAt = Instant.now();
    }

    /** 由撤回事务调用：撤回一旦生效不可撤销。 */
    public void markWithdrawn(Instant at) {
        if (this.withdrawnAt == null) {
            this.withdrawnAt = at;
        }
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /**
     * 判断该同意在给定时刻是否支持指定用途：未撤回、在有效期窗口内、用途匹配。
     */
    public boolean supports(String purpose, Instant at) {
        if (isWithdrawn()) {
            return false;
        }
        if (at.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && !at.isBefore(validUntil)) {
            return false;
        }
        return allowedPurposes.contains(purpose);
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public Set<String> getAllowedPurposes() {
        return Set.copyOf(allowedPurposes);
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
