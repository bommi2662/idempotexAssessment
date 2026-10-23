package com.fde.processor.model.entity;

import com.fde.processor.model.enums.FailureType;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.model.enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_records", indexes = {
        @Index(name = "idx_txn_id", columnList = "transactionId"),
        @Index(name = "idx_req_id", columnList = "requestId"),
        @Index(name = "idx_acc_seq", columnList = "accountId, sequenceNumber"),
        @Index(name = "idx_status", columnList = "status")
})
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String transactionId;

    @Column(nullable = false, length = 64)
    private String requestId;

    @Column(nullable = false)
    private Long sequenceNumber;

    @Column(nullable = false, length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FailureType failureType = FailureType.NONE;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private int maxRetries = 3;

    @Column(nullable = false)
    private boolean simulateTransientFailure = false;

    private Instant eventTime;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant processedAt;

    public TransactionRecord() {
    }

    public TransactionRecord(String transactionId, String requestId, Long sequenceNumber, String accountId,
                             TransactionType transactionType, BigDecimal amount, String currency,
                             Instant eventTime, boolean simulateTransientFailure) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.sequenceNumber = sequenceNumber;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "USD";
        this.eventTime = (eventTime != null) ? eventTime : Instant.now();
        this.simulateTransientFailure = simulateTransientFailure;
        this.status = TransactionStatus.RECEIVED;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isSimulateTransientFailure() {
        return simulateTransientFailure;
    }

    public void setSimulateTransientFailure(boolean simulateTransientFailure) {
        this.simulateTransientFailure = simulateTransientFailure;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
