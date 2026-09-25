package com.chris64233.cc.biobank.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.biobank.domain.SampleEvent;

public interface SampleEventRepository extends JpaRepository<SampleEvent, Long> {

    List<SampleEvent> findBySampleIdOrderBySequenceAsc(Long sampleId);

    List<SampleEvent> findAllByEventGroupIdOrderByIdAsc(long eventGroupId);
}
