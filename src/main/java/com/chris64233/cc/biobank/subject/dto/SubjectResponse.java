package com.chris64233.cc.biobank.subject.dto;

import com.chris64233.cc.biobank.subject.Subject;
import java.time.Instant;

public record SubjectResponse(
        Long id,
        String subjectCode,
        Instant createdAt) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getSubjectCode(),
                subject.getCreatedAt());
    }
}
