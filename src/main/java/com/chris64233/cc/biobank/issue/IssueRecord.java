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
 * subjectId 冗余保存，支持受试者维度的历史领用查询。
 */
@Entity
@Table(name = "issue_records")
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    /** 本次领用实际采用的同意版本（快照以 JSON 形式存于 response_body）。 */
    @Column(name = "consent_version_id", nullable = false)
    private Long consentVersionId;

    @Column(name = "request_hash", nullable = false, length = 512)
    private String requestHash;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IssueRecord() {
    }

    public IssueRecord(String idempotencyKey, Long subjectId, String purpose,
            Long consentVersionId, String requestHash, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.subjectId = subjectId;
        this.purpose = purpose;
        this.consentVersionId = consentVersionId;
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

    public Long getSubjectId() {
        return subjectId;
    }

    public String getPurpose() {
        return purpose;
    }

    public Long getConsentVersionId() {
        return consentVersionId;
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
