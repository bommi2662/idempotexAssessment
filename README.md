# Idempotent Background Transaction Processing Service (FDE L2)

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net)
[![Spring Boot 3.2.5](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/Tests-100%25%20Passing-emerald.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)]()

> **Practical Forward Deployed Engineer (FDE L2) Assessment**  
> An enterprise-grade, resilient background transaction processing service that processes asynchronous requests idempotently, detects duplicates, handles out-of-order delivery with automatic cascade draining, manages transient failure retries, and provides full operational visibility with an embedded live web console.

---

## Highlights & Features

- **Two-Tier Idempotency (L2-FR-05, L2-FR-06):** Deduplicates by producer Request ID (`requestId`) and business transaction ID (`transactionId`). Prevents double-balance updates.
- **Out-of-Order Sequence Management (L2-FR-07):** Option A sequence-based processing with stream watermark tracking. Automatically buffers premature sequences as `PENDING_SEQUENCE` and cascade-executes them the instant missing predecessors land.
- **Resilience & Transient Failure Retries (L2-FR-08, L2-FR-09):** Automated background retry worker with max attempt enforcement and retry exhaustion to `FAILED`.
- **Fault Isolation (L2-FR-02):** Malformed or negative amount transactions are isolated as `FAILED (VALIDATION_ERROR)` without stopping or blocking the batch.
- **Embedded Web Console & Live UI:** Interactive single-page dashboard at `http://localhost:8080/` with one-click execution of the Section 9.8 test scenario, live ledger balances, sequence buffer inspector, and audit log terminal.
- **Observability (L2-FR-11):** Structured logging with MDC (`txnId`, `reqId`), Spring Boot Actuator metrics (`/actuator/metrics`), and immutable audit trail (`audit_log_entries`).

---

## Quickstart

### Prerequisites
- **Java 17+** (JDK 17 or higher)
- **Maven 3.8+** (or use the system Maven)

### 1. Build and Run the Application
Open a terminal in the project root directory (`D:\Naga`):

```bash
mvn clean package
mvn spring-boot:run
```

Once started, the service listens on port `8080`.

### 2. Access the Interactive Web Dashboard
Open your web browser and navigate to:
```
http://localhost:8080/
```

From this console, you can:
1. Click **"Run L2 Test Batch (9.8)"** to execute the official assessment test scenario with visual results.
2. Click **"Trigger Retries"** to immediately invoke the background retry engine for `RETRY_PENDING` records.
3. Inspect real-time account ledger balances, the out-of-order sequence buffer, and the live audit event stream.
4. Upload custom JSON or CSV batch files.

---

## Running Automated Tests

Run the full suite of unit, concurrency, retry, and integration tests:

```bash
mvn test
```

### Test Coverage Highlights:
- **`L2ScenarioIntegrationTest`**: Executes the exact 6-transaction scenario from Section 9.8, asserting correct status transitions, duplicate suppression, cascade execution, and post-retry balances (`ACC-201` = $85.00, `ACC-203` = $500.00).
- **`ConcurrencyIntegrationTest`**: Fires simultaneous requests across multiple threads to verify thread-safe idempotency without race conditions or deadlocks.
- **`TransactionEngineTest`**: Unit tests verifying debit/credit ledger balance calculations, validation rejection of negative amounts, out-of-order buffering, and cascade draining.
- **`RetryServiceTest`**: Tests simulated transient failures, successful background recovery, and terminal retry exhaustion after max attempts.

---

## Section 9.8 Assessment Test Scenario Walkthrough

The project includes pre-configured sample data matching the exact test cases specified in **Section 9.8**:

| Input Order | Transaction ID | Request ID | Sequence | Account ID | Type | Amount | Expected Outcome | Actual Service Result |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|:---|
| **1** | `TXN-2002` | `REQ-2` | 2 | `ACC-201` | DEBIT | 40.00 | Pending (out-of-order) | **`PENDING_SEQUENCE`** (Buffered) |
| **2** | `TXN-2001` | `REQ-1` | 1 | `ACC-201` | CREDIT | 100.00 | Processed | **`PROCESSED`** (Triggers cascade drain of `TXN-2002`; balance becomes $60.00) |
| **3** | `TXN-2002` | `REQ-2-DUP` | 2 | `ACC-201` | DEBIT | 40.00 | Duplicate if already processed | **`DUPLICATE`** (Suppressed; balance stays $60.00) |
| **4** | `TXN-2003` | `REQ-3` | 3 | `ACC-201` | CREDIT | 25.00 | Processed | **`PROCESSED`** (Balance becomes $85.00) |
| **5** | `TXN-2004` | `REQ-4` | 4 | `ACC-202` | DEBIT | -10.00 | Failed validation | **`FAILED`** (Isolated; stream continues) |
| **6** | `TXN-2005` | `REQ-5` | 5 | `ACC-203` | CREDIT | 500.00 | Retry once, then processed | **`RETRY_PENDING`** &rarr; Retried &rarr; **`PROCESSED`** (Balance becomes $500.00) |

---

## REST API Documentation

### 1. Ingestion Endpoints
- **`POST /api/transactions/process-batch`**  
  Ingests a batch of transaction requests. Supports `asynchronous: true/false`.
  ```json
  {
    "asynchronous": false,
    "transactions": [
      {
        "transactionId": "TXN-2001",
        "requestId": "REQ-1",
        "sequenceNumber": 1,
        "accountId": "ACC-201",
        "transactionType": "CREDIT",
        "amount": 100.00,
        "currency": "USD"
      }
    ]
  }
  ```

- **`POST /api/transactions/single`**  
  Ingests a single transaction request.

- **`POST /api/transactions/upload-file`**  
  Accepts a multipart file upload (`.json` array or `.csv` file).

### 2. Simulation & Test Endpoints
- **`POST /api/simulation/l2-test-data`**  
  Executes the official 6-row Section 9.8 test batch.
- **`POST /api/simulation/trigger-retries`**  
  Immediately processes all records in `RETRY_PENDING` status.
- **`POST /api/simulation/reset`**  
  Resets all database tables and sequence watermarks to clean state.

### 3. Dashboard & Observability Endpoints
- **`GET /api/dashboard/summary`**  
  Returns total received, processed, duplicates prevented, buffered, retries pending, and active account balances.
- **`GET /api/dashboard/ledgers`**  
  Returns list of all accounts with current balances and sequence watermarks.
- **`GET /api/dashboard/pending-buffer`**  
  Returns list of transactions currently paused in the sequence buffer.
- **`GET /api/dashboard/audit-logs`**  
  Returns recent structured audit events.
- **`GET /actuator/health`** & **`GET /actuator/metrics`**  
  Standard Spring Boot production operational metrics.

---

## Sample Data Files

Sample files are available in the [`sample-data/`](sample-data/) directory:
- [`sample-data/l2-assessment-input.json`](sample-data/l2-assessment-input.json): Section 9.8 test input in JSON format.
- [`sample-data/l2-assessment-input.csv`](sample-data/l2-assessment-input.csv): Section 9.8 test input in CSV format.
- [`sample-data/l2-assessment-output.json`](sample-data/l2-assessment-output.json): Expected output and summary report.
- [`sample-data/edge-cases-batch.json`](sample-data/edge-cases-batch.json): Reversals, sequence gaps, and multi-account edge cases.

---

## Architectural Deep Dive

For the complete architectural design note covering trade-offs, concurrency control, AI-assisted development usage, and production hardening recommendations, please refer to:

👉 **[DESIGN.md](DESIGN.md)**
