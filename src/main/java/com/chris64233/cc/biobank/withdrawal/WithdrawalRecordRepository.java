package com.chris64233.cc.biobank.withdrawal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRecordRepository extends JpaRepository<WithdrawalRecord, Long> {

    Optional<WithdrawalRecord> findByWithdrawalKey(String withdrawalKey);

    List<WithdrawalRecord> findBySubjectCodeOrderByIdAsc(String subjectCode);
}
