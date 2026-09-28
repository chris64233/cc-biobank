package com.chris64233.cc.biobank.disposition;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositionRecordRepository extends JpaRepository<DispositionRecord, Long> {

    Optional<DispositionRecord> findByDispositionNo(String dispositionNo);

    List<DispositionRecord> findBySubjectIdOrderByIdAsc(Long subjectId);
}
