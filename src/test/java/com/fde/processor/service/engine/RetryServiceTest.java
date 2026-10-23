package com.fde.processor.service.engine;

import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.AuditLogEntryRepository;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RetryServiceTest {

    @Autowired
    private TransactionEngine transactionEngine;

    @Autowired
    private RetryService retryService;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private AccountLedgerRepository accountLedgerRepository;

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
    @DisplayName("Should simulate transient failure, transition to RETRY_PENDING, and successfully recover upon retry")
    void testTransientFailureAndRecovery() {
        // Step 1: Submit transaction with simulateTransientFailure = true
        TransactionRequestDto req = new TransactionRequestDto(
                "TXN-RETRY-1", "REQ-R1", 1L, "ACC-RET-1",
                TransactionType.CREDIT, new BigDecimal("500.00"), "USD", null, true
        );

        TransactionResponseDto initialResp = transactionEngine.process(req);
        assertThat(initialResp.getStatus()).isEqualTo(TransactionStatus.RETRY_PENDING);
        assertThat(initialResp.getFailureType()).isEqualTo(FailureType.TRANSIENT_SIMULATED);
        assertThat(initialResp.getRetryCount()).isEqualTo(1);

        // Account balance should remain 0 before retry
        assertThat(accountLedgerRepository.findById("ACC-RET-1")).isEmpty();

        // Step 2: Trigger retry service
        List<TransactionResponseDto> retryOutcomes = retryService.processPendingRetries();
        assertThat(retryOutcomes).hasSize(1);
        assertThat(retryOutcomes.get(0).getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(retryOutcomes.get(0).getCurrentBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

        // Verify database state after recovery
        Optional<TransactionRecord> updatedRecord = transactionRecordRepository.findByTransactionId("TXN-RETRY-1");
        assertThat(updatedRecord).isPresent();
        assertThat(updatedRecord.get().getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(updatedRecord.get().getRetryCount()).isEqualTo(2);

        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-RET-1");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should mark transaction as FAILED with RETRY_EXHAUSTED when retry attempts exceed maximum limit")
    void testRetryExhaustion() {
        TransactionRecord record = new TransactionRecord(
                "TXN-EXHAUST", "REQ-EX", 1L, "ACC-EX",
                TransactionType.DEBIT, new BigDecimal("100.00"), "USD", null, false
        );
        record.setStatus(TransactionStatus.RETRY_PENDING);
        record.setRetryCount(3);
        record.setMaxRetries(3);
        transactionRecordRepository.save(record);

        // Retrying an already exhausted record
        TransactionResponseDto resp = retryService.retryRecord(record);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(resp.getFailureType()).isEqualTo(FailureType.RETRY_EXHAUSTED);
        assertThat(resp.getErrorMessage()).contains("Max retry attempts (3) exhausted");

        Optional<TransactionRecord> updated = transactionRecordRepository.findByTransactionId("TXN-EXHAUST");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(updated.get().getFailureType()).isEqualTo(FailureType.RETRY_EXHAUSTED);
    }
}
