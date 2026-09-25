package com.chris64233.cc.biobank.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.biobank.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
