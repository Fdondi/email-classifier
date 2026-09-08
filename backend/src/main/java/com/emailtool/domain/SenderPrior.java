package com.emailtool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sender_prior")
public class SenderPrior {

    @Id
    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "open_rate", nullable = false)
    private double openRate = 0.5;

    @Column(name = "reply_rate", nullable = false)
    private double replyRate = 0.5;

    @Column(name = "time_to_archive_ms")
    private Long timeToArchiveMs;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public double getOpenRate() {
        return openRate;
    }

    public void setOpenRate(double openRate) {
        this.openRate = openRate;
    }

    public double getReplyRate() {
        return replyRate;
    }

    public void setReplyRate(double replyRate) {
        this.replyRate = replyRate;
    }

    public Long getTimeToArchiveMs() {
        return timeToArchiveMs;
    }

    public void setTimeToArchiveMs(Long timeToArchiveMs) {
        this.timeToArchiveMs = timeToArchiveMs;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
    }

    public double blendedPrior() {
        if (sampleCount <= 0) {
            return 0.5;
        }
        return (openRate + replyRate) / 2.0;
    }
}
