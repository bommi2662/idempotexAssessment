package com.fde.processor.model.entity;

import com.fde.processor.model.enums.TransactionStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "idx_idem_txn_id", columnList = "transactionId")
})
public class IdempotencyKey {

    @Id
    @Column(nullable = false, length = 64)
    private String idempotencyKey; // mapped from requestId

    @Column(nullable = false, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Lob
    @Column(length = 4096)
    private String responsePayload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public IdempotencyKey() {
    }

    public IdempotencyKey(String idempotencyKey, String transactionId, TransactionStatus status, String responsePayload) {
        this.idempotencyKey = idempotencyKey;
        this.transactionId = transactionId;
        this.status = status;
        this.responsePayload = responsePayload;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
