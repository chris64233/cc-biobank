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

    List<Aliquot> findBySampleSubjectIdOrderByIdAsc(Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aliquot a where a.id in :ids order by a.id asc")
    List<Aliquot> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * 撤回事务使用：按 id 升序锁定受试者名下处于指定状态的全部分装，
     * 与领用事务的加锁顺序（aliquot id 升序）保持一致，避免交叉死锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Aliquot a
            where a.sample.subject.id = :subjectId and a.status = :status
            order by a.id asc
            """)
    List<Aliquot> findBySubjectIdAndStatusForUpdate(@Param("subjectId") Long subjectId,
            @Param("status") AliquotStatus status);
}
