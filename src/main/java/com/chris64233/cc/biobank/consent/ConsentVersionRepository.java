package com.chris64233.cc.biobank.consent;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentVersionRepository extends JpaRepository<ConsentVersion, Long> {

    Optional<ConsentVersion> findBySubjectIdAndVersion(Long subjectId, String version);

    List<ConsentVersion> findBySubjectIdOrderByIdAsc(Long subjectId);

    /** 撤回时锁定受试者全部同意版本。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsentVersion c where c.subject.id = :subjectId order by c.id asc")
    List<ConsentVersion> findBySubjectIdForUpdate(@Param("subjectId") Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsentVersion c where c.id = :id")
    Optional<ConsentVersion> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsentVersion c where c.subject.id = :subjectId and c.version = :version")
    Optional<ConsentVersion> findBySubjectIdAndVersionForUpdate(
            @Param("subjectId") Long subjectId, @Param("version") String version);
}
