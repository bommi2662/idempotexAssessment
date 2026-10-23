package com.fde.processor.controller;

import com.fde.processor.model.dto.BatchSummaryResponse;
import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.AuditLogEntryRepository;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.engine.RetryService;
import com.fde.processor.service.ingestion.TransactionIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final TransactionIngestionService ingestionService;
    private final RetryService retryService;
    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final com.fde.processor.service.engine.SequenceManager sequenceManager;

    public SimulationController(TransactionIngestionService ingestionService,
                                RetryService retryService,
                                TransactionRecordRepository transactionRecordRepository,
                                AccountLedgerRepository accountLedgerRepository,
                                IdempotencyKeyRepository idempotencyKeyRepository,
                                AuditLogEntryRepository auditLogEntryRepository,
                                com.fde.processor.service.engine.SequenceManager sequenceManager) {
        this.ingestionService = ingestionService;
        this.retryService = retryService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.accountLedgerRepository = accountLedgerRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.sequenceManager = sequenceManager;
    }

    /**
     * Executes the exact L2 Practical Assessment test data from Section 9.8.
     */
    @PostMapping("/l2-test-data")
    public ResponseEntity<BatchSummaryResponse> runL2TestData() {
        List<TransactionRequestDto> list = new ArrayList<>();

        // 1. TXN-2002 | REQ-2 | seq 2 | ACC-201 | DEBIT | 40 -> Pending (Out of order)
        list.add(new TransactionRequestDto(
                "TXN-2002", "REQ-2", 2L, "ACC-201",
                TransactionType.DEBIT, new BigDecimal("40.00"), "USD", null, false
        ));

        // 2. TXN-2001 | REQ-1 | seq 1 | ACC-201 | CREDIT | 100 -> Processed (triggers cascade drain of TXN-2002!)
        list.add(new TransactionRequestDto(
                "TXN-2001", "REQ-1", 1L, "ACC-201",
                TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null, false
        ));

        // 3. TXN-2002 | REQ-2-DUP | seq 2 | ACC-201 | DEBIT | 40 -> Duplicate detected
        list.add(new TransactionRequestDto(
                "TXN-2002", "REQ-2-DUP", 2L, "ACC-201",
                TransactionType.DEBIT, new BigDecimal("40.00"), "USD", null, false
        ));

        // 4. TXN-2003 | REQ-3 | seq 3 | ACC-201 | CREDIT | 25 -> Processed
        list.add(new TransactionRequestDto(
                "TXN-2003", "REQ-3", 3L, "ACC-201",
                TransactionType.CREDIT, new BigDecimal("25.00"), "USD", null, false
        ));

        // 5. TXN-2004 | REQ-4 | seq 4 | ACC-202 | DEBIT | -10 -> Failed validation (isolated)
        list.add(new TransactionRequestDto(
                "TXN-2004", "REQ-4", 4L, "ACC-202",
                TransactionType.DEBIT, new BigDecimal("-10.00"), "USD", null, false
        ));

        // 6. TXN-2005 | REQ-5 | seq 5 | ACC-203 | CREDIT | 500 -> Retry once, then processed
        list.add(new TransactionRequestDto(
                "TXN-2005", "REQ-5", 5L, "ACC-203",
                TransactionType.CREDIT, new BigDecimal("500.00"), "USD", null, true
        ));

        BatchSummaryResponse summary = ingestionService.processBatch(list, false);
        return ResponseEntity.ok(summary);
    }

    /**
     * Manually triggers immediate retry processing for all records in RETRY_PENDING status.
     */
    @PostMapping("/trigger-retries")
    public ResponseEntity<List<TransactionResponseDto>> triggerRetries() {
        List<TransactionResponseDto> outcomes = retryService.processPendingRetries();
        return ResponseEntity.ok(outcomes);
    }

    /**
     * Resets the entire transaction database to a clean state.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetState() {
        sequenceManager.reset();
        auditLogEntryRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transactionRecordRepository.deleteAll();
        accountLedgerRepository.deleteAll();
        return ResponseEntity.ok(Map.of("message", "System state reset successfully. All ledgers, watermarks, and logs cleared."));
    }
}
