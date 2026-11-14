package com.emailtool.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "job_mail_scan")
public class JobMailScan {

    @Id
    @Column(name = "email_id")
    private Long emailId;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(nullable = false)
    private int postings;

    private String error;

    protected JobMailScan() {}

    public JobMailScan(Long emailId, Instant scannedAt, int postings, String error) {
        this.emailId = emailId;
        this.scannedAt = scannedAt;
        this.postings = postings;
        this.error = error;
    }

    public Long getEmailId() {
        return emailId;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public int getPostings() {
        return postings;
    }

    public String getError() {
        return error;
    }
}
