package com.fde.processor.controller;

import com.fde.processor.model.dto.BatchRequestDto;
import com.fde.processor.model.dto.BatchSummaryResponse;
import com.fde.processor.model.dto.TransactionRequestDto;
import com.fde.processor.model.dto.TransactionResponseDto;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.repository.TransactionRecordRepository;
import com.fde.processor.service.ingestion.TransactionIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionIngestionService ingestionService;
    private final TransactionRecordRepository transactionRecordRepository;

    public TransactionController(TransactionIngestionService ingestionService,
                                 TransactionRecordRepository transactionRecordRepository) {
        this.ingestionService = ingestionService;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    /**
     * Ingests a batch of transaction requests.
     */
    @PostMapping("/process-batch")
    public ResponseEntity<BatchSummaryResponse> processBatch(@RequestBody @Valid BatchRequestDto batchRequest) {
        BatchSummaryResponse summary = ingestionService.processBatch(
                batchRequest.getTransactions(), batchRequest.isAsynchronous());
        return ResponseEntity.ok(summary);
    }

    /**
     * Ingests a single transaction request.
     */
    @PostMapping("/single")
    public ResponseEntity<TransactionResponseDto> processSingle(@RequestBody @Valid TransactionRequestDto request) {
        TransactionResponseDto response = ingestionService.processSingle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Uploads and processes a transaction batch file (JSON or CSV).
     */
    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchSummaryResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        BatchSummaryResponse summary = ingestionService.processUploadedFile(file);
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves recent transaction records.
     */
    @GetMapping
    public ResponseEntity<List<TransactionRecord>> getRecentTransactions() {
        return ResponseEntity.ok(transactionRecordRepository.findTop200ByOrderByCreatedAtDesc());
    }

    /**
     * Retrieves a single transaction record by business transactionId.
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionRecord> getTransaction(@PathVariable String transactionId) {
        return transactionRecordRepository.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
