package com.chris64233.cc.biobank.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 受试者：样本归属与同意管理的主体，业务键为 subjectCode。
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
