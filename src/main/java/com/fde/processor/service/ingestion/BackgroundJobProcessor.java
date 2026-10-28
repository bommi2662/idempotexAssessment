package com.fde.processor.service.ingestion;

import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.service.engine.TransactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BackgroundJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobProcessor.class);

    private final TransactionEngine transactionEngine;

    public BackgroundJobProcessor(TransactionEngine transactionEngine) {
        this.transactionEngine = transactionEngine;
    }

    @Async("transactionExecutor")
    public CompletableFuture<TransactionResponseDto> processAsync(TransactionRequestDto request) {
        log.info("Background worker [{}] picked up transaction [{}]",
                Thread.currentThread().getName(), request.getTransactionId());
        try {
            TransactionResponseDto response = transactionEngine.process(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Unhandled exception in background worker for transaction [{}]", request.getTransactionId(), e);
            TransactionResponseDto errorResp = TransactionResponseDto.failed(
                    request.getTransactionId(),
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getSequenceNumber(),
                    FailureType.SYSTEM_ERROR,
                    "Worker execution error: " + e.getMessage()
            );
            return CompletableFuture.completedFuture(errorResp);
        }
    }
}
