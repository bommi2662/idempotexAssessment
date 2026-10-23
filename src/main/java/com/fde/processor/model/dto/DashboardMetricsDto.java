package com.fde.processor.model.dto;

import java.math.BigDecimal;
import java.util.Map;

public class DashboardMetricsDto {

    private long totalReceived;
    private long totalProcessed;
    private long totalDuplicatesPrevented;
    private long totalPendingSequence;
    private long totalRetryPending;
    private long totalFailed;
    private BigDecimal totalCreditVolume;
    private BigDecimal totalDebitVolume;
    private int activeAccountsCount;
    private Map<String, Long> statusCounts;

    public DashboardMetricsDto() {
    }

    public long getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(long totalReceived) {
        this.totalReceived = totalReceived;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(long totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public long getTotalDuplicatesPrevented() {
        return totalDuplicatesPrevented;
    }

    public void setTotalDuplicatesPrevented(long totalDuplicatesPrevented) {
        this.totalDuplicatesPrevented = totalDuplicatesPrevented;
    }

    public long getTotalPendingSequence() {
        return totalPendingSequence;
    }

    public void setTotalPendingSequence(long totalPendingSequence) {
        this.totalPendingSequence = totalPendingSequence;
    }

    public long getTotalRetryPending() {
        return totalRetryPending;
    }

    public void setTotalRetryPending(long totalRetryPending) {
        this.totalRetryPending = totalRetryPending;
    }

    public long getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(long totalFailed) {
        this.totalFailed = totalFailed;
    }

    public BigDecimal getTotalCreditVolume() {
        return totalCreditVolume;
    }

    public void setTotalCreditVolume(BigDecimal totalCreditVolume) {
        this.totalCreditVolume = totalCreditVolume;
    }

    public BigDecimal getTotalDebitVolume() {
        return totalDebitVolume;
    }

    public void setTotalDebitVolume(BigDecimal totalDebitVolume) {
        this.totalDebitVolume = totalDebitVolume;
    }

    public int getActiveAccountsCount() {
        return activeAccountsCount;
    }

    public void setActiveAccountsCount(int activeAccountsCount) {
        this.activeAccountsCount = activeAccountsCount;
    }

    public Map<String, Long> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<String, Long> statusCounts) {
        this.statusCounts = statusCounts;
    }
}
