package com.fde.processor.service.engine;

import com.fde.processor.model.dto.BatchSummaryResponse;
import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.AuditLogEntryRepository;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.ingestion.TransactionIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class L2ScenarioIntegrationTest {

    @Autowired
    private TransactionIngestionService ingestionService;

    @Autowired
    private RetryService retryService;

    @Autowired
    private AccountLedgerRepository accountLedgerRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Autowired
    private SequenceManager sequenceManager;

    @BeforeEach
    void setUp() {
        sequenceManager.reset();
        auditLogEntryRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transactionRecordRepository.deleteAll();
        accountLedgerRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End Execution of Section 9.8 L2 Test Data Batch with Out-of-Order Cascade, Deduplication, and Retry")
    void testSection98L2TestDataBatch() {
        List<TransactionRequestDto> batch = new ArrayList<>();

        // 1. TXN-2002 | REQ-2 | seq 2 | ACC-201 | DEBIT 40
        batch.add(new TransactionRequestDto("TXN-2002", "REQ-2", 2L, "ACC-201",
                TransactionType.DEBIT, new BigDecimal("40.00"), "USD", null, false));

        // 2. TXN-2001 | REQ-1 | seq 1 | ACC-201 | CREDIT 100
        batch.add(new TransactionRequestDto("TXN-2001", "REQ-1", 1L, "ACC-201",
                TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null, false));

        // 3. TXN-2002 | REQ-2-DUP | seq 2 | ACC-201 | DEBIT 40
        batch.add(new TransactionRequestDto("TXN-2002", "REQ-2-DUP", 2L, "ACC-201",
                TransactionType.DEBIT, new BigDecimal("40.00"), "USD", null, false));

        // 4. TXN-2003 | REQ-3 | seq 3 | ACC-201 | CREDIT 25
        batch.add(new TransactionRequestDto("TXN-2003", "REQ-3", 3L, "ACC-201",
                TransactionType.CREDIT, new BigDecimal("25.00"), "USD", null, false));

        // 5. TXN-2004 | REQ-4 | seq 4 | ACC-202 | DEBIT -10 (invalid amount)
        batch.add(new TransactionRequestDto("TXN-2004", "REQ-4", 4L, "ACC-202",
                TransactionType.DEBIT, new BigDecimal("-10.00"), "USD", null, false));

        // 6. TXN-2005 | REQ-5 | seq 5 | ACC-203 | CREDIT 500 (transient failure simulated)
        batch.add(new TransactionRequestDto("TXN-2005", "REQ-5", 5L, "ACC-203",
                TransactionType.CREDIT, new BigDecimal("500.00"), "USD", null, true));

        // Ingest the batch
        BatchSummaryResponse summary = ingestionService.processBatch(batch, false);

        assertThat(summary.getTotalReceived()).isEqualTo(6);
        assertThat(summary.getResults()).hasSize(6);

        // Result 1: TXN-2002 initially arrived out of order -> PENDING_SEQUENCE
        TransactionResponseDto r1 = summary.getResults().get(0);
        assertThat(r1.getTransactionId()).isEqualTo("TXN-2002");
        assertThat(r1.getStatus()).isEqualTo(TransactionStatus.PENDING_SEQUENCE);

        // Result 2: TXN-2001 arrived -> PROCESSED (and automatically drained TXN-2002)
        TransactionResponseDto r2 = summary.getResults().get(1);
        assertThat(r2.getTransactionId()).isEqualTo("TXN-2001");
        assertThat(r2.getStatus()).isEqualTo(TransactionStatus.PROCESSED);

        // Result 3: TXN-2002 duplicate arrived -> DUPLICATE
        TransactionResponseDto r3 = summary.getResults().get(2);
        assertThat(r3.getTransactionId()).isEqualTo("TXN-2002");
        assertThat(r3.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(r3.isDuplicate()).isTrue();

        // Result 4: TXN-2003 -> PROCESSED
        TransactionResponseDto r4 = summary.getResults().get(3);
        assertThat(r4.getTransactionId()).isEqualTo("TXN-2003");
        assertThat(r4.getStatus()).isEqualTo(TransactionStatus.PROCESSED);

        // Result 5: TXN-2004 -> FAILED (Validation isolated)
        TransactionResponseDto r5 = summary.getResults().get(4);
        assertThat(r5.getTransactionId()).isEqualTo("TXN-2004");
        assertThat(r5.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(r5.getFailureType()).isEqualTo(FailureType.VALIDATION_ERROR);

        // Result 6: TXN-2005 -> RETRY_PENDING
        TransactionResponseDto r6 = summary.getResults().get(5);
        assertThat(r6.getTransactionId()).isEqualTo("TXN-2005");
        assertThat(r6.getStatus()).isEqualTo(TransactionStatus.RETRY_PENDING);

        // Verify Account ACC-201 Balance:
        // Seq 1 (CREDIT 100) -> 100
        // Seq 2 (DEBIT 40 cascade drained) -> 60
        // Seq 2 DUP (DEBIT 40 suppressed) -> 60
        // Seq 3 (CREDIT 25) -> 85
        Optional<AccountLedger> ledger201 = accountLedgerRepository.findById("ACC-201");
        assertThat(ledger201).isPresent();
        assertThat(ledger201.get().getBalance()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(ledger201.get().getLastProcessedSequence()).isEqualTo(3L);

        // Now trigger the retry processor for TXN-2005
        List<TransactionResponseDto> retryResults = retryService.processPendingRetries();
        assertThat(retryResults).hasSize(1);
        assertThat(retryResults.get(0).getTransactionId()).isEqualTo("TXN-2005");
        assertThat(retryResults.get(0).getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(retryResults.get(0).getCurrentBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

        // Verify Account ACC-203 Balance after retry
        Optional<AccountLedger> ledger203 = accountLedgerRepository.findById("ACC-203");
        assertThat(ledger203).isPresent();
        assertThat(ledger203.get().getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(ledger203.get().getLastProcessedSequence()).isEqualTo(5L);

        // Verify Audit Log captured all transitions
        assertThat(auditLogEntryRepository.count()).isGreaterThanOrEqualTo(6);
    }
}
