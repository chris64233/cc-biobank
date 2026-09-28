package com.chris64233.cc.biobank.sample;

import jakarta.persistence.LockModeType;
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
    @Query("select s from Sample s where s.subjectId = :subjectId order by s.id asc")
    List<Sample> findBySubjectIdForUpdate(@Param("subjectId") Long subjectId);
}
