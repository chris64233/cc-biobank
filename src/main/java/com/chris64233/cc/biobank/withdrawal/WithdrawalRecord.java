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
 * 撤回幂等记录。撤回号唯一；response_body 保存首次冻结范围快照，重放原样返回。
 */
@Entity
@Table(name = "withdrawal_records")
public class WithdrawalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "withdrawal_no", nullable = false, unique = true, length = 128)
    private String withdrawalNo;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WithdrawalRecord() {
    }

    public WithdrawalRecord(String withdrawalNo, Long subjectId, String responseBody) {
        this.withdrawalNo = withdrawalNo;
        this.subjectId = subjectId;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getWithdrawalNo() {
        return withdrawalNo;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
