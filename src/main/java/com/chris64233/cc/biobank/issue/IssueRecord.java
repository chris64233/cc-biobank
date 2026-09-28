package com.chris64233.cc.biobank.issue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 领用幂等记录：相同幂等键 + 相同内容重放返回原结果，内容不同返回冲突。
 * 同时固化本次领用实际采用的受试者、同意版本与用途快照——同意事后撤回或过期
 * 都不会改写这里已经完成的领用。
 */
@Entity
@Table(name = "issue_records")
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Column(name = "consent_version_code", nullable = false, length = 64)
    private String consentVersionCode;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Column(name = "request_hash", nullable = false, length = 512)
    private String requestHash;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IssueRecord() {
    }

    public IssueRecord(String idempotencyKey, String subjectCode, String consentVersionCode,
            String purpose, String requestHash, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.subjectCode = subjectCode;
        this.consentVersionCode = consentVersionCode;
        this.purpose = purpose;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getConsentVersionCode() {
        return consentVersionCode;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
