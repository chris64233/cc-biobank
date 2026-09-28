package com.chris64233.cc.biobank.sample;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    boolean existsByExternalId(String externalId);

    List<Sample> findBySubjectIdOrderByIdAsc(Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.subject.id = :subjectId order by s.id asc")
    List<Sample> findBySubjectIdForUpdate(@Param("subjectId") Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id in :ids order by s.id asc")
    List<Sample> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
