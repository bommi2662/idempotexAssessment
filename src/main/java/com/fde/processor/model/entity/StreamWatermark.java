package com.fde.processor.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stream_watermarks")
public class StreamWatermark {

    @Id
    @Column(nullable = false, length = 64)
    private String channelId;

    @Column(nullable = false)
    private Long currentSequence = 0L;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public StreamWatermark() {
    }

    public StreamWatermark(String channelId, Long currentSequence) {
        this.channelId = channelId;
        this.currentSequence = currentSequence != null ? currentSequence : 0L;
        this.updatedAt = Instant.now();
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Long getCurrentSequence() {
        return currentSequence;
    }

    public void setCurrentSequence(Long currentSequence) {
        this.currentSequence = currentSequence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
