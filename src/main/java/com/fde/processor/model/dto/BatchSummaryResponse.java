package com.fde.processor.model.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchSummaryResponse {

    private String batchId;
    private int totalReceived;
    private int processedCount;
    private int duplicateCount;
    private int pendingSequenceCount;
    private int retryPendingCount;
    private int failedCount;
    private long executionTimeMs;
    private Map<String, BigDecimal> accountBalances = new HashMap<>();
    private List<TransactionResponseDto> results = new ArrayList<>();

    public BatchSummaryResponse() {
    }

    public void incrementProcessed() {
        this.processedCount++;
    }

    public void incrementDuplicate() {
        this.duplicateCount++;
    }

    public void incrementPendingSequence() {
        this.pendingSequenceCount++;
    }

    public void incrementRetryPending() {
        this.retryPendingCount++;
    }

    public void incrementFailed() {
        this.failedCount++;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public int getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(int totalReceived) {
        this.totalReceived = totalReceived;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getPendingSequenceCount() {
        return pendingSequenceCount;
    }

    public void setPendingSequenceCount(int pendingSequenceCount) {
        this.pendingSequenceCount = pendingSequenceCount;
    }

    public int getRetryPendingCount() {
        return retryPendingCount;
    }

    public void setRetryPendingCount(int retryPendingCount) {
        this.retryPendingCount = retryPendingCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Map<String, BigDecimal> getAccountBalances() {
        return accountBalances;
    }

    public void setAccountBalances(Map<String, BigDecimal> accountBalances) {
        this.accountBalances = accountBalances;
    }

    public List<TransactionResponseDto> getResults() {
        return results;
    }

    public void setResults(List<TransactionResponseDto> results) {
        this.results = results;
    }
}
