package com.chris64233.cc.biobank.disposition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 处置幂等记录。处置号唯一；response_body 保存首次处置结果快照，重放原样返回。
 * subjectId 冗余保存，支持受试者维度的处置进度查询。
 */
@Entity
@Table(name = "disposition_records")
public class DispositionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposition_no", nullable = false, unique = true, length = 128)
    private String dispositionNo;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DispositionRecord() {
    }

    public DispositionRecord(String dispositionNo, Long subjectId, String action,
            String responseBody) {
        this.dispositionNo = dispositionNo;
        this.subjectId = subjectId;
        this.action = action;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDispositionNo() {
        return dispositionNo;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getAction() {
        return action;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
