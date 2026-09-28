package com.chris64233.cc.biobank.withdrawal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 撤回幂等记录：同一撤回号重放返回首次结果，不重复冻结。
 */
@Entity
@Table(name = "withdrawal_records")
public class WithdrawalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务幂等的撤回号。 */
    @Column(name = "withdrawal_key", nullable = false, unique = true, length = 128)
    private String withdrawalKey;

    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WithdrawalRecord() {
    }

    public WithdrawalRecord(String withdrawalKey, String subjectCode, String responseBody) {
        this.withdrawalKey = withdrawalKey;
        this.subjectCode = subjectCode;
        this.createdAt = Instant.now();
        this.responseBody = responseBody;
    }

    public Long getId() {
        return id;
    }

    public String getWithdrawalKey() {
        return withdrawalKey;
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
