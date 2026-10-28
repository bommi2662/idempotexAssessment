package com.fde.processor.service.engine;

import com.fde.processor.model.entity.StreamWatermark;
import com.fde.processor.model.entity.TransactionRecord;
import com.fde.processor.model.enums.TransactionStatus;
import com.fde.processor.repository.StreamWatermarkRepository;
import com.fde.processor.repository.TransactionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SequenceManager {

    private static final Logger log = LoggerFactory.getLogger(SequenceManager.class);
    public static final String DEFAULT_CHANNEL = "PRIMARY_STREAM";

    private final StreamWatermarkRepository streamWatermarkRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    public SequenceManager(StreamWatermarkRepository streamWatermarkRepository,
                           TransactionRecordRepository transactionRecordRepository) {
        this.streamWatermarkRepository = streamWatermarkRepository;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    public enum SequenceDecision {
        READY_TO_PROCESS,
        OUT_OF_ORDER_PENDING,
        OBSOLETE_SEQUENCE
    }

    /**
     * Determines whether the given sequence number is next in line, out of order, or obsolete.
     */
    @Transactional(readOnly = true)
    public SequenceDecision evaluate(String accountId, Long incomingSequence) {
        long watermark = getWatermark();
        long expectedNext = watermark + 1L;

        if (incomingSequence == expectedNext) {
            return SequenceDecision.READY_TO_PROCESS;
        } else if (incomingSequence > expectedNext) {
            List<Long> missing = getMissingSequences(incomingSequence);
            log.info("Out-of-order sequence detected. Incoming: {}, Expected: {} (Watermark: {}). Missing predecessor sequences: {}. Queuing as PENDING_SEQUENCE.",
                    incomingSequence, expectedNext, watermark, missing);
            return SequenceDecision.OUT_OF_ORDER_PENDING;
        } else {
            log.info("Obsolete/already handled sequence [{}] (Watermark: {})", incomingSequence, watermark);
            return SequenceDecision.OBSOLETE_SEQUENCE;
        }
    }

    /**
     * Calculates the list of missing sequences between the current watermark and the incoming sequence (L2 Section 9.5 Option A).
     */
    @Transactional(readOnly = true)
    public List<Long> getMissingSequences(Long incomingSequence) {
        long watermark = getWatermark();
        List<Long> missing = new java.util.ArrayList<>();
        for (long seq = watermark + 1L; seq < incomingSequence; seq++) {
            missing.add(seq);
        }
        return missing;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void advanceWatermark(Long newSequence) {
        StreamWatermark watermark = streamWatermarkRepository.findById(DEFAULT_CHANNEL)
                .orElse(new StreamWatermark(DEFAULT_CHANNEL, 0L));

        if (newSequence > watermark.getCurrentSequence()) {
            watermark.setCurrentSequence(newSequence);
            watermark.setUpdatedAt(Instant.now());
            streamWatermarkRepository.save(watermark);
            log.info("Stream sequence watermark advanced to: {}", newSequence);
        }
    }

    @Transactional(readOnly = true)
    public long getWatermark() {
        return streamWatermarkRepository.findById(DEFAULT_CHANNEL)
                .map(StreamWatermark::getCurrentSequence)
                .orElse(0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void reset() {
        StreamWatermark watermark = streamWatermarkRepository.findById(DEFAULT_CHANNEL)
                .orElse(new StreamWatermark(DEFAULT_CHANNEL, 0L));
        watermark.setCurrentSequence(0L);
        watermark.setUpdatedAt(Instant.now());
        streamWatermarkRepository.save(watermark);
    }

    /**
     * Finds the next consecutive pending record in the buffer, if any.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionRecord> findNextBufferedTransaction(Long expectedSequence) {
        return transactionRecordRepository.findBySequenceNumberAndStatus(
                expectedSequence, TransactionStatus.PENDING_SEQUENCE);
    }

    /**
     * Retrieves all pending transactions across all accounts waiting in the sequence buffer.
     */
    @Transactional(readOnly = true)
    public List<TransactionRecord> getAllPendingBuffer() {
        return transactionRecordRepository.findByStatus(TransactionStatus.PENDING_SEQUENCE);
    }
}
