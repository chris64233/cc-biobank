package com.chris64233.cc.biobank.disposal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisposalItemRepository extends JpaRepository<DisposalItem, Long> {

    List<DisposalItem> findBySubjectCodeOrderByIdAsc(String subjectCode);

    List<DisposalItem> findByDisposalKeyOrderByIdAsc(String disposalKey);
}
