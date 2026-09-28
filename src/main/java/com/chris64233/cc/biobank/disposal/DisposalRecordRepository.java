package com.chris64233.cc.biobank.disposal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisposalRecordRepository extends JpaRepository<DisposalRecord, Long> {

    Optional<DisposalRecord> findByDisposalKey(String disposalKey);

    List<DisposalRecord> findBySubjectCodeOrderByIdAsc(String subjectCode);
}
