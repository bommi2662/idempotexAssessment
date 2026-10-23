package com.fde.processor.model.entity;

import com.fde.processor.model.enums.TransactionStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log_entries", indexes = {
        @Index(name = "idx_audit_txn", columnList = "transactionId"),
        @Index(name = "idx_audit_req", columnList = "requestId"),
        @Index(name = "idx_audit_acc", columnList = "accountId"),
        @Index(name = "idx_audit_time", columnList = "timestamp")
})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String transactionId;

    @Column(length = 64)
    private String requestId;

    @Column(length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus toStatus;

    @Column(nullable = false, length = 1024)
    private String details;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AuditLogEntry() {
    }

    public AuditLogEntry(String transactionId, String requestId, String accountId,
                         TransactionStatus fromStatus, TransactionStatus toStatus, String details) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.details = details;
        this.timestamp = Instant.now();
    }

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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public TransactionStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(TransactionStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public TransactionStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(TransactionStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
