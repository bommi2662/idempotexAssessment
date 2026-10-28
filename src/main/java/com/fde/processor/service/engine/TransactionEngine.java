package com.fde.processor.service.engine;

import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionEngine {

    private static final Logger log = LoggerFactory.getLogger(TransactionEngine.class);

    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final IdempotencyService idempotencyService;
    private final SequenceManager sequenceManager;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, Object> streamLocks = new ConcurrentHashMap<>();

    public TransactionEngine(TransactionRecordRepository transactionRecordRepository,
                             AccountLedgerRepository accountLedgerRepository,
                             IdempotencyService idempotencyService,
                             SequenceManager sequenceManager,
                             AuditService auditService,
                             PlatformTransactionManager transactionManager) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.accountLedgerRepository = accountLedgerRepository;
        this.idempotencyService = idempotencyService;
        this.sequenceManager = sequenceManager;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Ingests and processes a transaction request safely under duplicate, concurrency, and out-of-order conditions.
     */
    public TransactionResponseDto process(TransactionRequestDto request) {
        try {
            MDC.put("transactionId", request.getTransactionId() != null ? request.getTransactionId() : "-");
            MDC.put("requestId", request.getRequestId() != null ? request.getRequestId() : "-");

            // Synchronize on the stream coordination channel so sequence evaluation,
            // watermark progression, and waterfall drain are strictly serialized and race-free.
            Object streamLock = streamLocks.computeIfAbsent(SequenceManager.DEFAULT_CHANNEL, k -> new Object());

            synchronized (streamLock) {
                return transactionTemplate.execute(status -> doProcess(request));
            }
        } finally {
            MDC.remove("transactionId");
            MDC.remove("requestId");
        }
    }

    private TransactionResponseDto doProcess(TransactionRequestDto request) {
        log.info("Processing transaction [{}] reqId=[{}] account=[{}] seq=[{}] amount=[{} {}]",
                request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                request.getSequenceNumber(), request.getAmount(), request.getCurrency());

        // 1. Validation check (L2-FR-02)
        String validationError = validateRequest(request);
        if (validationError != null) {
            return handleValidationError(request, validationError);
        }

        // 2. Idempotency & Deduplication Check (L2-FR-05, L2-FR-06)
        Optional<TransactionResponseDto> existingOutcome =
                idempotencyService.findExistingProcessedOutcome(request.getRequestId(), request.getTransactionId());

        if (existingOutcome.isPresent()) {
            TransactionResponseDto duplicateResponse = existingOutcome.get();
            recordDuplicateArrival(request, duplicateResponse);
            return duplicateResponse;
        }

        // 3. Sequence Evaluation (L2-FR-07: Option A Sequence-Based Processing)
        SequenceManager.SequenceDecision decision =
                sequenceManager.evaluate(request.getAccountId(), request.getSequenceNumber());

        if (decision == SequenceManager.SequenceDecision.OUT_OF_ORDER_PENDING) {
            return bufferOutOfOrderTransaction(request);
        } else if (decision == SequenceManager.SequenceDecision.OBSOLETE_SEQUENCE) {
            return handleObsoleteSequence(request);
        }

        // 4. Ready to Process
        return executeTransaction(request);
    }

    /**
     * Executes the business transaction against the ledger and then drains any buffered subsequent sequences.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponseDto executeTransaction(TransactionRequestDto request) {
        TransactionRecord record = getOrCreateRecord(request);
        record.setStatus(TransactionStatus.PROCESSING);
        record.setSimulateTransientFailure(request.isSimulateTransientFailure());
        transactionRecordRepository.save(record);

        auditService.recordEvent(request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                TransactionStatus.RECEIVED, TransactionStatus.PROCESSING, "Transaction execution started");

        // Simulate transient failure scenario if flagged (L2-FR-08)
        if (record.isSimulateTransientFailure() && record.getRetryCount() == 0) {
            record.setRetryCount(1);
            record.setStatus(TransactionStatus.RETRY_PENDING);
            record.setFailureType(FailureType.TRANSIENT_SIMULATED);
            record.setErrorMessage("Simulated transient downstream service timeout (Retry 1/3)");
            transactionRecordRepository.save(record);

            auditService.recordEvent(record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    TransactionStatus.PROCESSING, TransactionStatus.RETRY_PENDING,
                    "Simulated transient failure on attempt 1. Queued for background retry.");

            TransactionResponseDto response = TransactionResponseDto.retryPending(
                    record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    record.getSequenceNumber(), record.getRetryCount(), record.getErrorMessage());

            idempotencyService.recordOutcome(record.getRequestId(), record.getTransactionId(),
                    TransactionStatus.RETRY_PENDING, response);

            return response;
        }

        // Post to ledger atomically (L2-FR-05)
        AccountLedger ledger = accountLedgerRepository.findById(request.getAccountId())
                .orElseGet(() -> new AccountLedger(request.getAccountId(), BigDecimal.ZERO, 0L));

        BigDecimal oldBalance = ledger.getBalance();
        BigDecimal newBalance = calculateNewBalance(oldBalance, request.getTransactionType(), request.getAmount());

        ledger.setBalance(newBalance);
        ledger.setLastProcessedSequence(request.getSequenceNumber());
        ledger.setUpdatedAt(Instant.now());
        accountLedgerRepository.save(ledger);

        // Advance stream sequence watermark ONLY upon successful commit (L2-FR-07)
        sequenceManager.advanceWatermark(request.getSequenceNumber());

        // Update transaction record
        record.setStatus(TransactionStatus.PROCESSED);
        record.setFailureType(FailureType.NONE);
        record.setErrorMessage(null);
        record.setProcessedAt(Instant.now());
        transactionRecordRepository.save(record);

        auditService.recordEvent(record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                TransactionStatus.PROCESSING, TransactionStatus.PROCESSED,
                String.format("Applied %s of %s. Balance updated from %s to %s. Sequence advanced to %d.",
                        request.getTransactionType(), request.getAmount(), oldBalance, newBalance, request.getSequenceNumber()));

        TransactionResponseDto response = new TransactionResponseDto();
        response.setTransactionId(record.getTransactionId());
        response.setRequestId(record.getRequestId());
        response.setAccountId(record.getAccountId());
        response.setSequenceNumber(record.getSequenceNumber());
        response.setTransactionType(record.getTransactionType());
        response.setAmount(record.getAmount());
        response.setCurrency(record.getCurrency());
        response.setStatus(TransactionStatus.PROCESSED);
        response.setCurrentBalance(newBalance);
        response.setDuplicate(false);
        response.setProcessedAt(record.getProcessedAt());

        // Cache in idempotency store
        idempotencyService.recordOutcome(record.getRequestId(), record.getTransactionId(),
                TransactionStatus.PROCESSED, response);

        // 5. Automatic Cascade Drain: Check if next sequence is buffered in memory/db!
        drainBufferedSequences(request.getSequenceNumber() + 1);

        return response;
    }

    /**
     * Drains consecutive buffered out-of-order transactions in sequence order without bypassing failure simulation.
     */
    private void drainBufferedSequences(Long nextExpectedSequence) {
        Long currentSeq = nextExpectedSequence;
        while (true) {
            Optional<TransactionRecord> nextPendingOpt =
                    sequenceManager.findNextBufferedTransaction(currentSeq);

            if (nextPendingOpt.isEmpty()) {
                break;
            }

            TransactionRecord pendingRecord = nextPendingOpt.get();
            log.info("Cascading execution for previously buffered transaction [{}] seq=[{}] account=[{}]",
                    pendingRecord.getTransactionId(), pendingRecord.getSequenceNumber(), pendingRecord.getAccountId());

            auditService.recordEvent(pendingRecord.getTransactionId(), pendingRecord.getRequestId(), pendingRecord.getAccountId(),
                    TransactionStatus.PENDING_SEQUENCE, TransactionStatus.PROCESSING,
                    "Predecessor sequence satisfied. Releasing from buffer for processing.");

            TransactionRequestDto pendingDto = new TransactionRequestDto(
                    pendingRecord.getTransactionId(),
                    pendingRecord.getRequestId(),
                    pendingRecord.getSequenceNumber(),
                    pendingRecord.getAccountId(),
                    pendingRecord.getTransactionType(),
                    pendingRecord.getAmount(),
                    pendingRecord.getCurrency(),
                    pendingRecord.getEventTime(),
                    pendingRecord.isSimulateTransientFailure()
            );

            TransactionResponseDto cascadeResp = executeTransaction(pendingDto);

            // If the drained transaction encountered a simulated transient failure or failed, pause cascade
            if (cascadeResp.getStatus() == TransactionStatus.RETRY_PENDING ||
                cascadeResp.getStatus() == TransactionStatus.FAILED) {
                break;
            }

            currentSeq++;
        }
    }

    private BigDecimal calculateNewBalance(BigDecimal current, TransactionType type, BigDecimal amount) {
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        return switch (type) {
            case CREDIT -> current.add(amount);
            case DEBIT -> current.subtract(amount);
            case REVERSAL -> current.add(amount); // Reverses prior debit by refunding credit
        };
    }

    private String validateRequest(TransactionRequestDto request) {
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            return "transactionId is required";
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            return "requestId is required";
        }
        if (request.getAccountId() == null || request.getAccountId().isBlank()) {
            return "accountId is required";
        }
        if (request.getSequenceNumber() == null || request.getSequenceNumber() <= 0) {
            return "sequenceNumber must be greater than 0";
        }
        if (request.getTransactionType() == null) {
            return "transactionType is required (CREDIT, DEBIT, REVERSAL)";
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "amount must be greater than 0";
        }
        return null;
    }

    private TransactionResponseDto handleValidationError(TransactionRequestDto request, String error) {
        log.warn("Validation failure on transaction [{}]: {}", request.getTransactionId(), error);
        TransactionRecord record = getOrCreateRecord(request);
        record.setStatus(TransactionStatus.FAILED);
        record.setFailureType(FailureType.VALIDATION_ERROR);
        record.setErrorMessage(error);
        transactionRecordRepository.save(record);

        // Advance sequence watermark past the invalid record so malformed records do not halt the stream (L2-FR-02)
        if (request.getSequenceNumber() != null && request.getSequenceNumber() == sequenceManager.getWatermark() + 1) {
            sequenceManager.advanceWatermark(request.getSequenceNumber());
            // Trigger cascade drain for any subsequent valid sequences waiting behind this failed record
            drainBufferedSequences(request.getSequenceNumber() + 1);
        }

        auditService.recordEvent(request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                TransactionStatus.RECEIVED, TransactionStatus.FAILED, "Validation failed: " + error);

        TransactionResponseDto response = TransactionResponseDto.failed(request.getTransactionId(), request.getRequestId(),
                request.getAccountId(), request.getSequenceNumber(), FailureType.VALIDATION_ERROR, error);

        idempotencyService.recordOutcome(request.getRequestId(), request.getTransactionId(),
                TransactionStatus.FAILED, response);

        return response;
    }

    private TransactionResponseDto bufferOutOfOrderTransaction(TransactionRequestDto request) {
        TransactionRecord record = getOrCreateRecord(request);
        record.setStatus(TransactionStatus.PENDING_SEQUENCE);
        record.setFailureType(FailureType.SEQUENCE_OUT_OF_ORDER);
        List<Long> missing = sequenceManager.getMissingSequences(request.getSequenceNumber());
        String reason = "Out of order sequence: sequence " + request.getSequenceNumber() +
                " received before previous sequence completed. Missing predecessor sequence(s): " + missing + ". Buffered in pending store.";
        record.setErrorMessage(reason);
        transactionRecordRepository.save(record);

        auditService.recordEvent(request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                TransactionStatus.RECEIVED, TransactionStatus.PENDING_SEQUENCE, reason);

        TransactionResponseDto response = TransactionResponseDto.pendingSequence(request.getTransactionId(), request.getRequestId(),
                request.getAccountId(), request.getSequenceNumber(), reason);

        idempotencyService.recordOutcome(request.getRequestId(), request.getTransactionId(),
                TransactionStatus.PENDING_SEQUENCE, response);

        return response;
    }

    private TransactionResponseDto handleObsoleteSequence(TransactionRequestDto request) {
        AccountLedger ledger = accountLedgerRepository.findById(request.getAccountId())
                .orElse(new AccountLedger(request.getAccountId(), BigDecimal.ZERO, 0L));

        String msg = String.format("Request sequence %d is less than or equal to current stream sequence watermark %d.",
                request.getSequenceNumber(), sequenceManager.getWatermark());

        TransactionRecord record = getOrCreateRecord(request);
        record.setStatus(TransactionStatus.DUPLICATE);
        record.setFailureType(FailureType.NONE);
        record.setErrorMessage(msg);
        transactionRecordRepository.save(record);

        auditService.recordEvent(request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                TransactionStatus.RECEIVED, TransactionStatus.DUPLICATE, msg);

        TransactionResponseDto response = TransactionResponseDto.duplicate(request.getTransactionId(), request.getRequestId(),
                request.getAccountId(), request.getSequenceNumber(), ledger.getBalance(), msg);

        idempotencyService.recordOutcome(request.getRequestId(), request.getTransactionId(),
                TransactionStatus.DUPLICATE, response);

        return response;
    }

    private void recordDuplicateArrival(TransactionRequestDto request, TransactionResponseDto duplicateResponse) {
        auditService.recordEvent(request.getTransactionId(), request.getRequestId(), request.getAccountId(),
                TransactionStatus.RECEIVED, TransactionStatus.DUPLICATE,
                "Duplicate request suppressed. No balance change applied. " + duplicateResponse.getErrorMessage());
    }

    private TransactionRecord getOrCreateRecord(TransactionRequestDto request) {
        return transactionRecordRepository.findByTransactionId(request.getTransactionId())
                .orElseGet(() -> new TransactionRecord(
                        request.getTransactionId(),
                        request.getRequestId(),
                        request.getSequenceNumber(),
                        request.getAccountId(),
                        request.getTransactionType(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getEventTime(),
                        request.isSimulateTransientFailure()
                ));
    }
}
