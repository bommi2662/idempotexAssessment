package com.fde.processor.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_ledgers")
public class AccountLedger {

    @Id
    @Column(nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private Long lastProcessedSequence = 0L;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public AccountLedger() {
    }

    public AccountLedger(String accountId, BigDecimal initialBalance, Long initialSequence) {
        this.accountId = accountId;
        this.balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        this.lastProcessedSequence = initialSequence != null ? initialSequence : 0L;
        this.updatedAt = Instant.now();
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getLastProcessedSequence() {
        return lastProcessedSequence;
    }

    public void setLastProcessedSequence(Long lastProcessedSequence) {
        this.lastProcessedSequence = lastProcessedSequence;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
