package com.chris64233.cc.biobank.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 受试者。其全部原始样本、后代分装、领用、撤回与处置均按受试者维度归集。
 */
@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_code", nullable = false, unique = true, length = 64)
    private String subjectCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Subject() {
    }

    public Subject(String subjectCode) {
        this.subjectCode = subjectCode;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
