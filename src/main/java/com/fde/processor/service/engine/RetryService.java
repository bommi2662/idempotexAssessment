package com.fde.processor.service.engine;

import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    private final TransactionRecordRepository transactionRecordRepository;
    private final TransactionEngine transactionEngine;
    private final AuditService auditService;

    @Value("${processor.retry.max-attempts:3}")
    private int defaultMaxRetries = 3;

    @Value("${processor.retry.initial-backoff-ms:1000}")
    private long initialBackoffMs = 1000L;

    @Value("${processor.retry.multiplier:2.0}")
    private double backoffMultiplier = 2.0;

    public RetryService(TransactionRecordRepository transactionRecordRepository,
                        TransactionEngine transactionEngine,
                        AuditService auditService) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionEngine = transactionEngine;
        this.auditService = auditService;
    }

    /**
     * Periodic background worker to pick up and process transactions in RETRY_PENDING state.
     */
    @Scheduled(fixedDelayString = "${processor.retry.fixed-delay-ms:10000}", initialDelay = 5000)
    public void retryScheduledPendingTransactions() {
        processPendingRetries();
    }

    /**
     * Executes retry logic for all pending retry transactions.
     * Can also be called on demand via REST or test scenarios.
     */
    @Transactional
    public List<TransactionResponseDto> processPendingRetries() {
        List<TransactionRecord> pendingRetries =
                transactionRecordRepository.findByStatus(TransactionStatus.RETRY_PENDING);

        if (pendingRetries.isEmpty()) {
            return List.of();
        }

        log.info("Found {} transactions in RETRY_PENDING status to retry", pendingRetries.size());
        List<TransactionResponseDto> outcomes = new ArrayList<>();

        for (TransactionRecord record : pendingRetries) {
            outcomes.add(retryRecord(record));
        }

        return outcomes;
    }

    @Transactional
    public TransactionResponseDto retryRecord(TransactionRecord record) {
        try {
            MDC.put("transactionId", record.getTransactionId() != null ? record.getTransactionId() : "-");
            MDC.put("requestId", record.getRequestId() != null ? record.getRequestId() : "-");

            int currentAttempt = record.getRetryCount() + 1;
            int maxAttempts = record.getMaxRetries() > 0 ? record.getMaxRetries() : defaultMaxRetries;
            long backoffDelayMs = (long) (initialBackoffMs * Math.pow(backoffMultiplier, Math.max(0, currentAttempt - 1)));

            log.info("Retrying transaction [{}] attempt {} of {} (computed exponential backoff delay: {}ms)",
                    record.getTransactionId(), currentAttempt, maxAttempts, backoffDelayMs);

            if (currentAttempt > maxAttempts) {
                // Exhausted retries -> mark terminal FAILED
                record.setStatus(TransactionStatus.FAILED);
                record.setFailureType(FailureType.RETRY_EXHAUSTED);
                record.setErrorMessage(String.format("Max retry attempts (%d) exhausted without recovery.", maxAttempts));
                transactionRecordRepository.save(record);

                auditService.recordEvent(record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                        TransactionStatus.RETRY_PENDING, TransactionStatus.FAILED,
                        "Max retry attempts exhausted. Terminal failure recorded.");

                return TransactionResponseDto.failed(record.getTransactionId(), record.getRequestId(),
                        record.getAccountId(), record.getSequenceNumber(), FailureType.RETRY_EXHAUSTED, record.getErrorMessage());
            }

            record.setRetryCount(currentAttempt);
            record.setSimulateTransientFailure(false);
            transactionRecordRepository.save(record);

            // Convert record to request DTO for engine execution with simulated failure cleared
            TransactionRequestDto retryRequest = new TransactionRequestDto(
                    record.getTransactionId(),
                    record.getRequestId(),
                    record.getSequenceNumber(),
                    record.getAccountId(),
                    record.getTransactionType(),
                    record.getAmount(),
                    record.getCurrency(),
                    record.getEventTime(),
                    false // Clear transient simulation so it succeeds on retry
            );

            auditService.recordEvent(record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    TransactionStatus.RETRY_PENDING, TransactionStatus.PROCESSING,
                    String.format("Executing retry attempt %d/%d (backoff: %dms)", currentAttempt, maxAttempts, backoffDelayMs));

            return transactionEngine.executeTransaction(retryRequest);
        } finally {
            MDC.remove("transactionId");
            MDC.remove("requestId");
        }
    }
}
