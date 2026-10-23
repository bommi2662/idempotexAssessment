package com.fde.processor.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fde.processor.model.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionRequestDto {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotBlank(message = "requestId is required")
    @JsonAlias({"idempotencyKey", "reqId"})
    private String requestId;

    @NotNull(message = "sequenceNumber is required")
    @JsonAlias({"sequence", "seq"})
    private Long sequenceNumber;

    @NotBlank(message = "accountId is required")
    @JsonAlias({"accId", "account"})
    private String accountId;

    @NotNull(message = "transactionType is required")
    @JsonAlias({"type"})
    private TransactionType transactionType;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than 0")
    private BigDecimal amount;

    private String currency = "USD";

    private Instant eventTime;

    private boolean simulateTransientFailure = false;

    public TransactionRequestDto() {
    }

    public TransactionRequestDto(String transactionId, String requestId, Long sequenceNumber, String accountId,
                                 TransactionType transactionType, BigDecimal amount, String currency,
                                 Instant eventTime, boolean simulateTransientFailure) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.sequenceNumber = sequenceNumber;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.eventTime = eventTime;
        this.simulateTransientFailure = simulateTransientFailure;
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

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public boolean isSimulateTransientFailure() {
        return simulateTransientFailure;
    }

    public void setSimulateTransientFailure(boolean simulateTransientFailure) {
        this.simulateTransientFailure = simulateTransientFailure;
    }
}
