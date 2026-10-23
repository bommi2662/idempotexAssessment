package com.fde.processor.model.dto;

import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponseDto {

    private String transactionId;
    private String requestId;
    private String accountId;
    private Long sequenceNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private FailureType failureType;
    private String errorMessage;
    private BigDecimal currentBalance;
    private boolean duplicate;
    private int retryCount;
    private Instant processedAt;

    public TransactionResponseDto() {
    }

    public static TransactionResponseDto duplicate(String transactionId, String requestId, String accountId,
                                                   Long sequenceNumber, BigDecimal currentBalance, String msg) {
        TransactionResponseDto dto = new TransactionResponseDto();
        dto.setTransactionId(transactionId);
        dto.setRequestId(requestId);
        dto.setAccountId(accountId);
        dto.setSequenceNumber(sequenceNumber);
        dto.setStatus(TransactionStatus.DUPLICATE);
        dto.setFailureType(FailureType.NONE);
        dto.setErrorMessage(msg);
        dto.setCurrentBalance(currentBalance);
        dto.setDuplicate(true);
        dto.setProcessedAt(Instant.now());
        return dto;
    }

    public static TransactionResponseDto failed(String transactionId, String requestId, String accountId,
                                                Long sequenceNumber, FailureType failureType, String errorMessage) {
        TransactionResponseDto dto = new TransactionResponseDto();
        dto.setTransactionId(transactionId);
        dto.setRequestId(requestId);
        dto.setAccountId(accountId);
        dto.setSequenceNumber(sequenceNumber);
        dto.setStatus(TransactionStatus.FAILED);
        dto.setFailureType(failureType);
        dto.setErrorMessage(errorMessage);
        dto.setDuplicate(false);
        dto.setProcessedAt(Instant.now());
        return dto;
    }

    public static TransactionResponseDto pendingSequence(String transactionId, String requestId, String accountId,
                                                         Long sequenceNumber, String reason) {
        TransactionResponseDto dto = new TransactionResponseDto();
        dto.setTransactionId(transactionId);
        dto.setRequestId(requestId);
        dto.setAccountId(accountId);
        dto.setSequenceNumber(sequenceNumber);
        dto.setStatus(TransactionStatus.PENDING_SEQUENCE);
        dto.setFailureType(FailureType.SEQUENCE_OUT_OF_ORDER);
        dto.setErrorMessage(reason);
        dto.setDuplicate(false);
        dto.setProcessedAt(Instant.now());
        return dto;
    }

    public static TransactionResponseDto retryPending(String transactionId, String requestId, String accountId,
                                                      Long sequenceNumber, int retryCount, String reason) {
        TransactionResponseDto dto = new TransactionResponseDto();
        dto.setTransactionId(transactionId);
        dto.setRequestId(requestId);
        dto.setAccountId(accountId);
        dto.setSequenceNumber(sequenceNumber);
        dto.setStatus(TransactionStatus.RETRY_PENDING);
        dto.setFailureType(FailureType.TRANSIENT_SIMULATED);
        dto.setErrorMessage(reason);
        dto.setRetryCount(retryCount);
        dto.setDuplicate(false);
        dto.setProcessedAt(Instant.now());
        return dto;
    }

    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public void setFailureType(FailureType failureType) {
        this.failureType = failureType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
