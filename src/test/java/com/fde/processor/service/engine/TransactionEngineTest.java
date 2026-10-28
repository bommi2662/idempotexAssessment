package com.fde.processor.service.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.entity.IdempotencyKey;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.AuditLogEntryRepository;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransactionEngineTest {

    @Autowired
    private TransactionEngine transactionEngine;

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
    @DisplayName("Should successfully process valid sequential CREDIT transaction and update ledger")
    void testProcessValidCredit() {
        TransactionRequestDto req = new TransactionRequestDto(
                "TXN-101", "REQ-101", 1L, "ACC-100",
                TransactionType.CREDIT, new BigDecimal("150.00"), "USD", null, false
        );

        TransactionResponseDto resp = transactionEngine.process(req);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(resp.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(resp.isDuplicate()).isFalse();

        // Verify Ledger state
        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-100");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(ledger.get().getLastProcessedSequence()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should buffer out-of-order transaction as PENDING_SEQUENCE when sequence gap exists")
    void testBufferOutOfOrder() {
        TransactionRequestDto req = new TransactionRequestDto(
                "TXN-102", "REQ-102", 2L, "ACC-100",
                TransactionType.DEBIT, new BigDecimal("50.00"), "USD", null, false
        );

        TransactionResponseDto resp = transactionEngine.process(req);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.PENDING_SEQUENCE);
        assertThat(resp.getFailureType()).isEqualTo(FailureType.SEQUENCE_OUT_OF_ORDER);

        // Account ledger should remain untouched
        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-100");
        assertThat(ledger).isEmpty();
    }

    @Test
    @DisplayName("Should automatically cascade drain buffered out-of-order transaction once predecessor arrives")
    void testCascadeDrainOutOfOrder() {
        // Step 1: Arrive sequence 2 first
        TransactionRequestDto reqSeq2 = new TransactionRequestDto(
                "TXN-202", "REQ-202", 2L, "ACC-200",
                TransactionType.DEBIT, new BigDecimal("40.00"), "USD", null, false
        );
        TransactionResponseDto respSeq2 = transactionEngine.process(reqSeq2);
        assertThat(respSeq2.getStatus()).isEqualTo(TransactionStatus.PENDING_SEQUENCE);

        // Step 2: Arrive sequence 1
        TransactionRequestDto reqSeq1 = new TransactionRequestDto(
                "TXN-201", "REQ-201", 1L, "ACC-200",
                TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null, false
        );
        TransactionResponseDto respSeq1 = transactionEngine.process(reqSeq1);
        assertThat(respSeq1.getStatus()).isEqualTo(TransactionStatus.PROCESSED);

        // Verify that sequence 2 was automatically executed and drained!
        Optional<TransactionRecord> drainedRec = transactionRecordRepository.findByTransactionId("TXN-202");
        assertThat(drainedRec).isPresent();
        assertThat(drainedRec.get().getStatus()).isEqualTo(TransactionStatus.PROCESSED);

        // Final balance should be 100 - 40 = 60
        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-200");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getBalance()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(ledger.get().getLastProcessedSequence()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should detect duplicate request and suppress duplicate balance effect")
    void testDuplicateDetection() {
        // First execution
        TransactionRequestDto req1 = new TransactionRequestDto(
                "TXN-301", "REQ-301", 1L, "ACC-300",
                TransactionType.CREDIT, new BigDecimal("200.00"), "USD", null, false
        );
        transactionEngine.process(req1);

        // Duplicate request with same business transactionId but different requestId
        TransactionRequestDto reqDuplicate = new TransactionRequestDto(
                "TXN-301", "REQ-301-DUP", 1L, "ACC-300",
                TransactionType.CREDIT, new BigDecimal("200.00"), "USD", null, false
        );
        TransactionResponseDto respDup = transactionEngine.process(reqDuplicate);

        assertThat(respDup.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(respDup.isDuplicate()).isTrue();

        // Ledger balance must remain 200.00 (not 400.00)
        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-300");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("Should isolate and fail malformed record with negative amount without affecting system")
    void testNegativeAmountValidation() {
        TransactionRequestDto invalidReq = new TransactionRequestDto(
                "TXN-401", "REQ-401", 1L, "ACC-400",
                TransactionType.DEBIT, new BigDecimal("-50.00"), "USD", null, false
        );

        TransactionResponseDto resp = transactionEngine.process(invalidReq);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(resp.getFailureType()).isEqualTo(FailureType.VALIDATION_ERROR);
        assertThat(resp.getErrorMessage()).contains("amount must be greater than 0");

        // Ledger should not be created
        assertThat(accountLedgerRepository.findById("ACC-400")).isEmpty();
    }

    @Test
    @DisplayName("Should explicitly report missing predecessor sequences when out-of-order gap occurs (Section 9.5 Option A)")
    void testMissingSequenceReporting() {
        TransactionRequestDto req = new TransactionRequestDto(
                "TXN-GAP-3", "REQ-GAP-3", 3L, "ACC-GAP",
                TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null, false
        );

        TransactionResponseDto resp = transactionEngine.process(req);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.PENDING_SEQUENCE);
        assertThat(resp.getErrorMessage()).contains("Missing predecessor sequence(s): [1, 2]");
    }

    @Test
    @DisplayName("Should detect duplicate request even while original request is still in PENDING_SEQUENCE status")
    void testDuplicateDetectionWhilePending() {
        // Step 1: Submit out-of-order sequence 2 (buffered as PENDING_SEQUENCE)
        TransactionRequestDto req1 = new TransactionRequestDto(
                "TXN-PEND-DUP", "REQ-P1", 2L, "ACC-PEND",
                TransactionType.CREDIT, new BigDecimal("75.00"), "USD", null, false
        );
        TransactionResponseDto resp1 = transactionEngine.process(req1);
        assertThat(resp1.getStatus()).isEqualTo(TransactionStatus.PENDING_SEQUENCE);

        // Step 2: Retry with same transactionId while still pending
        TransactionRequestDto req2 = new TransactionRequestDto(
                "TXN-PEND-DUP", "REQ-P2-RETRY", 2L, "ACC-PEND",
                TransactionType.CREDIT, new BigDecimal("75.00"), "USD", null, false
        );
        TransactionResponseDto resp2 = transactionEngine.process(req2);

        assertThat(resp2.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(resp2.isDuplicate()).isTrue();
        assertThat(resp2.getErrorMessage()).contains("is currently PENDING_SEQUENCE");
    }
}
