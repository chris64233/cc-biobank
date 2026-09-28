package com.chris64233.cc.biobank.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleEventRepository extends JpaRepository<SampleEvent, Long> {

    List<SampleEvent> findBySampleIdOrderByIdAsc(Long sampleId);

    List<SampleEvent> findByAliquotIdOrderByIdAsc(Long aliquotId);

    List<SampleEvent> findBySampleIdInOrderByIdAsc(Collection<Long> sampleIds);
}
