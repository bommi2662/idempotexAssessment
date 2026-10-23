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

        List<TransactionRecord> all = transactionRecordRepository.findAll();
        dto.setTotalReceived(all.size());

        Map<String, Long> statusCounts = new HashMap<>();
        long processed = 0, duplicates = 0, pending = 0, retry = 0, failed = 0;
        BigDecimal creditVol = BigDecimal.ZERO;
        BigDecimal debitVol = BigDecimal.ZERO;

        for (TransactionRecord r : all) {
            String s = r.getStatus().name();
            statusCounts.put(s, statusCounts.getOrDefault(s, 0L) + 1L);

            switch (r.getStatus()) {
                case PROCESSED -> {
                    processed++;
                    if (r.getTransactionType() == TransactionType.CREDIT) {
                        creditVol = creditVol.add(r.getAmount());
                    } else if (r.getTransactionType() == TransactionType.DEBIT) {
                        debitVol = debitVol.add(r.getAmount());
                    }
                }
                case DUPLICATE -> duplicates++;
                case PENDING_SEQUENCE -> pending++;
                case RETRY_PENDING -> retry++;
                case FAILED -> failed++;
                default -> {}
            }
        }

        dto.setTotalProcessed(processed);
        dto.setTotalDuplicatesPrevented(duplicates);
        dto.setTotalPendingSequence(pending);
        dto.setTotalRetryPending(retry);
        dto.setTotalFailed(failed);
        dto.setTotalCreditVolume(creditVol);
        dto.setTotalDebitVolume(debitVol);
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
