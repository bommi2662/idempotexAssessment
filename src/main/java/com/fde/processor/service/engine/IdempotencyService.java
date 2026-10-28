package com.fde.processor.service.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.IdempotencyKey;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository,
                              TransactionRecordRepository transactionRecordRepository,
                              AccountLedgerRepository accountLedgerRepository,
                              ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.accountLedgerRepository = accountLedgerRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if the request is already processed or exists as duplicate.
     * Evaluates both requestId (idempotency key) and business transactionId.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionResponseDto> findExistingProcessedOutcome(String requestId, String transactionId) {
        // 1. Check idempotency key (requestId)
        Optional<IdempotencyKey> keyOpt = idempotencyKeyRepository.findByIdempotencyKey(requestId);
        if (keyOpt.isPresent()) {
            IdempotencyKey key = keyOpt.get();
            log.info("Idempotency key hit by requestId [{}] -> Status: {}", requestId, key.getStatus());
            Optional<TransactionResponseDto> cached = deserializeCachedResponse(key.getResponsePayload());
            if (cached.isPresent()) {
                TransactionResponseDto dto = cached.get();
                dto.setStatus(TransactionStatus.DUPLICATE);
                dto.setDuplicate(true);
                dto.setErrorMessage("Idempotent replay: requestId [" + requestId + "] previously recorded with status: " + key.getStatus());
                return Optional.of(dto);
            }
        }

        // 2. Check business transactionId deduplication
        Optional<TransactionRecord> txnOpt = transactionRecordRepository.findByTransactionId(transactionId);
        if (txnOpt.isPresent()) {
            TransactionRecord record = txnOpt.get();
            if (record.getStatus() == TransactionStatus.PROCESSED ||
                record.getStatus() == TransactionStatus.PENDING_SEQUENCE ||
                record.getStatus() == TransactionStatus.RETRY_PENDING) {

                log.info("Duplicate business transaction hit by transactionId [{}] (Original requestId: {}, status: {})",
                        transactionId, record.getRequestId(), record.getStatus());

                BigDecimal currentBal = accountLedgerRepository.findById(record.getAccountId())
                        .map(AccountLedger::getBalance).orElse(BigDecimal.ZERO);

                String msg = record.getStatus() == TransactionStatus.PROCESSED
                        ? "Duplicate request detected: transactionId [" + transactionId + "] was already PROCESSED under requestId [" + record.getRequestId() + "]"
                        : "Duplicate request detected: transactionId [" + transactionId + "] is currently " + record.getStatus() + " under requestId [" + record.getRequestId() + "]";

                return Optional.of(TransactionResponseDto.duplicate(
                        record.getTransactionId(),
                        requestId,
                        record.getAccountId(),
                        record.getSequenceNumber(),
                        currentBal,
                        msg
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Records or updates the idempotency key state.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordOutcome(String requestId, String transactionId, TransactionStatus status, TransactionResponseDto response) {
        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response for idempotency cache", e);
        }

        IdempotencyKey key = idempotencyKeyRepository.findByIdempotencyKey(requestId)
                .orElse(new IdempotencyKey(requestId, transactionId, status, payloadJson));

        key.setStatus(status);
        key.setResponsePayload(payloadJson);
        key.setUpdatedAt(Instant.now());
        idempotencyKeyRepository.save(key);
    }

    private Optional<TransactionResponseDto> deserializeCachedResponse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, TransactionResponseDto.class));
        } catch (Exception e) {
            log.error("Failed to deserialize cached response payload", e);
            return Optional.empty();
        }
    }
}
