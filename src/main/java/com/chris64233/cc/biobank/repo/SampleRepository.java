package com.chris64233.cc.biobank.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.biobank.domain.Sample;

import jakarta.persistence.LockModeType;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByExternalSampleNo(String externalSampleNo);

    boolean existsByExternalSampleNo(String externalSampleNo);

    List<Sample> findByParentIdOrderByIdAsc(Long parentId);

    /** 行级写锁，领用并发扣减时按 ID 排序加锁，杜绝负库存与丢失更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id in :ids order by s.id asc")
    List<Sample> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findByIdForUpdate(@Param("id") Long id);
}
