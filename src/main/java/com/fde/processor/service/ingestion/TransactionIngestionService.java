package com.fde.processor.service.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.processor.model.dto.BatchSummaryResponse;
import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.AccountLedger;
import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionType;
import com.fde.processor.repository.AccountLedgerRepository;
import com.fde.processor.service.engine.TransactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class TransactionIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestionService.class);

    private final TransactionEngine transactionEngine;
    private final BackgroundJobProcessor backgroundJobProcessor;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ObjectMapper objectMapper;

    public TransactionIngestionService(TransactionEngine transactionEngine,
                                       BackgroundJobProcessor backgroundJobProcessor,
                                       AccountLedgerRepository accountLedgerRepository,
                                       ObjectMapper objectMapper) {
        this.transactionEngine = transactionEngine;
        this.backgroundJobProcessor = backgroundJobProcessor;
        this.accountLedgerRepository = accountLedgerRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingests and processes a batch of transactions with isolation for malformed records.
     */
    public BatchSummaryResponse processBatch(List<TransactionRequestDto> requests, boolean async) {
        long startTime = System.currentTimeMillis();
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Starting ingestion for batch [{}] containing {} transactions (async={})",
                batchId, requests.size(), async);

        BatchSummaryResponse summary = new BatchSummaryResponse();
        summary.setBatchId(batchId);
        summary.setTotalReceived(requests.size());

        List<TransactionResponseDto> results = new ArrayList<>();

        if (async) {
            List<CompletableFuture<TransactionResponseDto>> futures = new ArrayList<>();
            for (TransactionRequestDto req : requests) {
                futures.add(backgroundJobProcessor.processAsync(req));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<TransactionResponseDto> future : futures) {
                try {
                    results.add(future.join());
                } catch (Exception e) {
                    log.error("Error joining async transaction future", e);
                }
            }
        } else {
            for (TransactionRequestDto req : requests) {
                try {
                    TransactionResponseDto resp = transactionEngine.process(req);
                    results.add(resp);
                } catch (Exception e) {
                    log.error("Unexpected error processing transaction [{}]", req.getTransactionId(), e);
                    results.add(TransactionResponseDto.failed(
                            req.getTransactionId(), req.getRequestId(), req.getAccountId(),
                            req.getSequenceNumber(), FailureType.SYSTEM_ERROR, e.getMessage()
                    ));
                }
            }
        }

        // Aggregate statistics
        for (TransactionResponseDto resp : results) {
            switch (resp.getStatus()) {
                case PROCESSED -> summary.incrementProcessed();
                case DUPLICATE -> summary.incrementDuplicate();
                case PENDING_SEQUENCE -> summary.incrementPendingSequence();
                case RETRY_PENDING -> summary.incrementRetryPending();
                case FAILED -> summary.incrementFailed();
                default -> {}
            }
        }

        // Query active account balances
        Map<String, BigDecimal> balances = new HashMap<>();
        for (AccountLedger ledger : accountLedgerRepository.findAll()) {
            balances.put(ledger.getAccountId(), ledger.getBalance());
        }
        summary.setAccountBalances(balances);
        summary.setResults(results);
        summary.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        log.info("Batch [{}] complete: {} PROCESSED, {} DUPLICATE, {} PENDING_SEQ, {} RETRY_PENDING, {} FAILED in {}ms",
                batchId, summary.getProcessedCount(), summary.getDuplicateCount(),
                summary.getPendingSequenceCount(), summary.getRetryPendingCount(),
                summary.getFailedCount(), summary.getExecutionTimeMs());

        return summary;
    }

    /**
     * Ingests a single transaction.
     */
    public TransactionResponseDto processSingle(TransactionRequestDto request) {
        return transactionEngine.process(request);
    }

    /**
     * Parses and processes an uploaded file (JSON array or CSV format).
     */
    public BatchSummaryResponse processUploadedFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        log.info("Ingesting uploaded transaction file: {}", filename);
        try {
            List<TransactionRequestDto> requests;
            if (filename != null && filename.toLowerCase().endsWith(".csv")) {
                requests = parseCsvFile(file);
            } else {
                requests = parseJsonFile(file);
            }
            return processBatch(requests, false);
        } catch (Exception e) {
            log.error("Failed to parse uploaded file: " + filename, e);
            throw new IllegalArgumentException("Failed to parse file: " + e.getMessage(), e);
        }
    }

    private List<TransactionRequestDto> parseJsonFile(MultipartFile file) throws Exception {
        return objectMapper.readValue(file.getInputStream(), new TypeReference<List<TransactionRequestDto>>() {});
    }

    private List<TransactionRequestDto> parseCsvFile(MultipartFile file) throws Exception {
        List<TransactionRequestDto> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    if (line.toLowerCase().contains("transactionid") || line.toLowerCase().contains("accountid")) {
                        continue; // Skip header
                    }
                }

                String[] tokens = line.split(",");
                if (tokens.length >= 6) {
                    try {
                        TransactionRequestDto dto = new TransactionRequestDto();
                        dto.setTransactionId(tokens[0].trim());
                        dto.setRequestId(tokens[1].trim());
                        dto.setSequenceNumber(Long.parseLong(tokens[2].trim()));
                        dto.setAccountId(tokens[3].trim());
                        dto.setTransactionType(TransactionType.valueOf(tokens[4].trim().toUpperCase()));
                        dto.setAmount(new BigDecimal(tokens[5].trim()));

                        if (tokens.length >= 7 && !tokens[6].trim().isEmpty()) {
                            dto.setCurrency(tokens[6].trim());
                        }
                        if (tokens.length >= 8 && !tokens[7].trim().isEmpty()) {
                            dto.setSimulateTransientFailure(Boolean.parseBoolean(tokens[7].trim()));
                        }
                        list.add(dto);
                    } catch (Exception ex) {
                        log.warn("Malformed CSV line [{}]: {}. Creating isolated invalid record.", line, ex.getMessage());
                        TransactionRequestDto badDto = new TransactionRequestDto();
                        badDto.setTransactionId(tokens[0].trim());
                        badDto.setRequestId(tokens.length > 1 ? tokens[1].trim() : "MALFORMED");
                        badDto.setAccountId(tokens.length > 3 ? tokens[3].trim() : "UNKNOWN");
                        badDto.setSequenceNumber(-1L);
                        badDto.setAmount(BigDecimal.valueOf(-1));
                        list.add(badDto);
                    }
                }
            }
        }
        return list;
    }
}
