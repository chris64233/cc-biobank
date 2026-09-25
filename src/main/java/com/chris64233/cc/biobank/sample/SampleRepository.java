package com.chris64233.cc.biobank.sample;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    boolean existsByExternalId(String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findByIdForUpdate(@Param("id") Long id);
}
