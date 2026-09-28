package com.chris64233.cc.biobank.subject;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findBySubjectIdOrderByIdAsc(Long subjectId);

    Optional<Consent> findBySubjectIdAndVersionCode(Long subjectId, String versionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consent c where c.subject.id = :subjectId and c.versionCode = :versionCode")
    Optional<Consent> findBySubjectIdAndVersionCodeForUpdate(
            @Param("subjectId") Long subjectId, @Param("versionCode") String versionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consent c where c.subject.id = :subjectId order by c.id asc")
    List<Consent> findBySubjectIdForUpdate(@Param("subjectId") Long subjectId);
}
