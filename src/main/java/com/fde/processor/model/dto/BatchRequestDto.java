package com.fde.processor.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class BatchRequestDto {

    private String batchId;

    @NotEmpty(message = "Transaction batch cannot be empty")
    @Valid
    private List<TransactionRequestDto> transactions = new ArrayList<>();

    private boolean asynchronous = false;

    public BatchRequestDto() {
    }

    public BatchRequestDto(List<TransactionRequestDto> transactions) {
        this.transactions = transactions;
    }

    public BatchRequestDto(String batchId, List<TransactionRequestDto> transactions, boolean asynchronous) {
        this.batchId = batchId;
        this.transactions = transactions;
        this.asynchronous = asynchronous;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<TransactionRequestDto> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionRequestDto> transactions) {
        this.transactions = transactions;
    }

    public boolean isAsynchronous() {
        return asynchronous;
    }

    public void setAsynchronous(boolean asynchronous) {
        this.asynchronous = asynchronous;
    }
}
