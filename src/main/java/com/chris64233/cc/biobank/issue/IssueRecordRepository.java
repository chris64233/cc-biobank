package com.chris64233.cc.biobank.issue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRecordRepository extends JpaRepository<IssueRecord, Long> {

    Optional<IssueRecord> findByIdempotencyKey(String idempotencyKey);

    List<IssueRecord> findBySubjectIdOrderByIdAsc(Long subjectId);
}
