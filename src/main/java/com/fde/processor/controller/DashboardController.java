package com.fde.processor.controller;

import com.fde.processor.model.dto.DashboardMetricsDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.entity.AuditLogEntry;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.AuditLogEntryRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.engine.SequenceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final SequenceManager sequenceManager;

    public DashboardController(TransactionRecordRepository transactionRecordRepository,
                               AccountLedgerRepository accountLedgerRepository,
                               AuditLogEntryRepository auditLogEntryRepository,
                               SequenceManager sequenceManager) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.accountLedgerRepository = accountLedgerRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.sequenceManager = sequenceManager;
    }

    /**
     * Aggregated metrics summary for the operational monitoring dashboard.
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardMetricsDto> getDashboardSummary() {
        DashboardMetricsDto dto = new DashboardMetricsDto();

        long total = transactionRecordRepository.count();
        dto.setTotalReceived((int) total);

        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : transactionRecordRepository.countGroupByStatus()) {
            TransactionStatus status = (TransactionStatus) row[0];
            Long count = (Long) row[1];
            statusCounts.put(status.name(), count);
        }

        long processed = statusCounts.getOrDefault(TransactionStatus.PROCESSED.name(), 0L);
        long duplicates = statusCounts.getOrDefault(TransactionStatus.DUPLICATE.name(), 0L);
        long pending = statusCounts.getOrDefault(TransactionStatus.PENDING_SEQUENCE.name(), 0L);
        long retry = statusCounts.getOrDefault(TransactionStatus.RETRY_PENDING.name(), 0L);
        long failed = statusCounts.getOrDefault(TransactionStatus.FAILED.name(), 0L);

        BigDecimal creditVol = transactionRecordRepository.sumAmountByStatusAndType(
                TransactionStatus.PROCESSED, TransactionType.CREDIT);
        BigDecimal debitVol = transactionRecordRepository.sumAmountByStatusAndType(
                TransactionStatus.PROCESSED, TransactionType.DEBIT);

        dto.setTotalProcessed(processed);
        dto.setTotalDuplicatesPrevented(duplicates);
        dto.setTotalPendingSequence(pending);
        dto.setTotalRetryPending(retry);
        dto.setTotalFailed(failed);
        dto.setTotalCreditVolume(creditVol != null ? creditVol : BigDecimal.ZERO);
        dto.setTotalDebitVolume(debitVol != null ? debitVol : BigDecimal.ZERO);
        dto.setStatusCounts(statusCounts);
        dto.setActiveAccountsCount((int) accountLedgerRepository.count());

        return ResponseEntity.ok(dto);
    }

    /**
     * Retrieves all active account ledgers with their current balance and sequence watermark.
     */
    @GetMapping("/ledgers")
    public ResponseEntity<List<AccountLedger>> getLedgers() {
        return ResponseEntity.ok(accountLedgerRepository.findAll());
    }

    /**
     * Retrieves all out-of-order transactions waiting in the pending sequence buffer.
     */
    @GetMapping("/pending-buffer")
    public ResponseEntity<List<TransactionRecord>> getPendingBuffer() {
        return ResponseEntity.ok(sequenceManager.getAllPendingBuffer());
    }

    /**
     * Retrieves the latest 100 immutable audit log entries.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogEntry>> getAuditLogs() {
        return ResponseEntity.ok(auditLogEntryRepository.findTop100ByOrderByTimestampDesc());
    }
}
