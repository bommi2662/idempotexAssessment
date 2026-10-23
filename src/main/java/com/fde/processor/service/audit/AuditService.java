package com.fde.processor.service.audit;

import com.fde.processor.model.entity.AuditLogEntry;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.repository.AuditLogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogEntryRepository auditLogEntryRepository;

    public AuditService(AuditLogEntryRepository auditLogEntryRepository) {
        this.auditLogEntryRepository = auditLogEntryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(String transactionId, String requestId, String accountId,
                            TransactionStatus fromStatus, TransactionStatus toStatus, String details) {
        try {
            MDC.put("transactionId", transactionId != null ? transactionId : "-");
            MDC.put("requestId", requestId != null ? requestId : "-");

            log.info("AUDIT [{}] {} -> {}: {}", accountId, fromStatus, toStatus, details);

            AuditLogEntry entry = new AuditLogEntry(transactionId, requestId, accountId, fromStatus, toStatus, details);
            auditLogEntryRepository.save(entry);
        } finally {
            MDC.remove("transactionId");
            MDC.remove("requestId");
        }
    }

    public List<AuditLogEntry> getRecentLogs() {
        return auditLogEntryRepository.findTop100ByOrderByTimestampDesc();
    }
}
