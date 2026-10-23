package com.fde.processor.service.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.IdempotencyKey;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.repository.IdempotencyKeyRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository,
                              TransactionRecordRepository transactionRecordRepository,
                              ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionRecordRepository = transactionRecordRepository;
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
            if (key.getStatus() == TransactionStatus.PROCESSED || key.getStatus() == TransactionStatus.DUPLICATE) {
                log.info("Idempotency hit by requestId [{}] -> Status: {}", requestId, key.getStatus());
                Optional<TransactionResponseDto> cached = deserializeCachedResponse(key.getResponsePayload());
                cached.ifPresent(c -> {
                    c.setStatus(TransactionStatus.DUPLICATE);
                    c.setDuplicate(true);
                    c.setErrorMessage("Idempotent replay: requestId [" + requestId + "] was previously processed");
                });
                return cached;
            }
        }

        // 2. Check business transactionId deduplication
        Optional<TransactionRecord> txnOpt = transactionRecordRepository.findByTransactionId(transactionId);
        if (txnOpt.isPresent()) {
            TransactionRecord record = txnOpt.get();
            if (record.getStatus() == TransactionStatus.PROCESSED) {
                log.info("Duplicate business transaction hit by transactionId [{}] (Original requestId: {})",
                        transactionId, record.getRequestId());
                return Optional.of(TransactionResponseDto.duplicate(
                        record.getTransactionId(),
                        requestId,
                        record.getAccountId(),
                        record.getSequenceNumber(),
                        null,
                        "Duplicate request detected: transactionId [" + transactionId + "] was already PROCESSED under requestId [" + record.getRequestId() + "]"
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
