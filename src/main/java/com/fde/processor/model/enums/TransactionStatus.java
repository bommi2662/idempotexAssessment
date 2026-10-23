package com.fde.processor.model.enums;

public enum TransactionStatus {
    RECEIVED,          // Ingested and validated; ready for processing
    PROCESSING,        // Under active processing by background worker
    PROCESSED,         // Successfully processed and posted to ledger
    DUPLICATE,         // Duplicate detected (by requestId or transactionId); no duplicate effect
    PENDING_SEQUENCE,  // Out-of-order; waiting for missing previous sequence(s)
    RETRY_PENDING,     // Transient failure encountered; queued for retry
    FAILED             // Terminal failure (validation, retry exhaustion, business rule failure)
}
