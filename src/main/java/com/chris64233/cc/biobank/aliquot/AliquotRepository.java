package com.chris64233.cc.biobank.aliquot;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AliquotRepository extends JpaRepository<Aliquot, Long> {

    List<Aliquot> findBySampleIdOrderByIdAsc(Long sampleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aliquot a where a.id in :ids order by a.id asc")
    List<Aliquot> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
