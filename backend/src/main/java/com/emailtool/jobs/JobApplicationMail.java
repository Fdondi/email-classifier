package com.emailtool.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A mail read for an application outcome; {@code change} is set when it changed the learning data. */
@Entity
@Table(name = "job_application_mail")
public class JobApplicationMail {

    /** Gmail message id in hex (as in the backfill), or "email:<id>" for mail without one. */
    @Id
    @Column(name = "mail_id")
    private String mailId;

    @Column(name = "email_id")
    private Long emailId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false)
    private Instant at;

    /** applied, received, viewed, forwarded, screening, interview, final, offer, rejection, withdrawn, other, not_application, ignore. */
    @Column(nullable = false)
    private String kind;

    private String summary;

    private String change;

    protected JobApplicationMail() {}

    JobApplicationMail(String mailId, Long emailId, Long applicationId, Instant at, String kind, String summary, String change) {
        this.mailId = mailId;
        this.emailId = emailId;
        this.applicationId = applicationId;
        this.at = at;
        this.kind = kind;
        this.summary = summary;
        this.change = change;
    }

    public String getMailId() {
        return mailId;
    }

    public Long getEmailId() {
        return emailId;
    }

    public Instant getAt() {
        return at;
    }

    public String getKind() {
        return kind;
    }

    public String getSummary() {
        return summary;
    }

    public String getChange() {
        return change;
    }
}
