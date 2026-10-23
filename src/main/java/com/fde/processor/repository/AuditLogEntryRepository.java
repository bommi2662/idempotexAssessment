package com.fde.processor.repository;

import com.fde.processor.model.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findTop100ByOrderByTimestampDesc();

    List<AuditLogEntry> findByTransactionIdOrderByTimestampDesc(String transactionId);

    List<AuditLogEntry> findByAccountIdOrderByTimestampDesc(String accountId);
}
