package com.fde.processor.repository;

import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByTransactionId(String transactionId);

    Optional<TransactionRecord> findByRequestId(String requestId);

    Optional<TransactionRecord> findByAccountIdAndSequenceNumber(String accountId, Long sequenceNumber);

    Optional<TransactionRecord> findBySequenceNumberAndStatus(Long sequenceNumber, TransactionStatus status);

    List<TransactionRecord> findByAccountIdAndStatusOrderBySequenceNumberAsc(String accountId, TransactionStatus status);

    List<TransactionRecord> findByStatus(TransactionStatus status);

    long countByStatus(TransactionStatus status);

    @Query("SELECT r.status, COUNT(r) FROM TransactionRecord r GROUP BY r.status")
    List<Object[]> countGroupByStatus();

    List<TransactionRecord> findTop200ByOrderByCreatedAtDesc();
}
