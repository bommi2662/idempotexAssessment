package com.fde.processor.service.engine;

import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ConcurrencyIntegrationTest {

    @Autowired
    private TransactionEngine transactionEngine;

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
    @DisplayName("Should maintain idempotency under concurrent replay of identical request across multiple threads")
    void testConcurrentDuplicateReplay() throws InterruptedException {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        List<TransactionResponseDto> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    TransactionRequestDto req = new TransactionRequestDto(
                            "TXN-CONCURRENT-1", "REQ-CONCURRENT-1", 1L, "ACC-CONC",
                            TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null, false
                    );
                    TransactionResponseDto resp = transactionEngine.process(req);
                    responses.add(resp);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Release all threads at once
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(responses).hasSize(threads);

        // Account balance must strictly be 100.00, NEVER multiplied!
        Optional<AccountLedger> ledger = accountLedgerRepository.findById("ACC-CONC");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Exactly one PROCESSED, all others DUPLICATE
        long processedCount = responses.stream().filter(r -> r.getStatus() == TransactionStatus.PROCESSED).count();
        long duplicateCount = responses.stream().filter(r -> r.getStatus() == TransactionStatus.DUPLICATE).count();

        assertThat(processedCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(threads - 1);
    }
}
