package com.chris64233.cc.biobank.disposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 处置幂等记录：同一处置号重放返回首次结果，不重复处置。
 */
@Entity
@Table(name = "disposal_records")
public class DisposalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposal_key", nullable = false, unique = true, length = 128)
    private String disposalKey;

    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DisposalRecord() {
    }

    public DisposalRecord(String disposalKey, String subjectCode, String responseBody) {
        this.disposalKey = disposalKey;
        this.subjectCode = subjectCode;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDisposalKey() {
        return disposalKey;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
