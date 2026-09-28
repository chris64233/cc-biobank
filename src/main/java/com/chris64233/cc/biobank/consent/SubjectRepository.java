package com.chris64233.cc.biobank.consent;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsBySubjectCode(String subjectCode);

    Optional<Subject> findBySubjectCode(String subjectCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.id = :id")
    Optional<Subject> findByIdForUpdate(@Param("id") Long id);
}
