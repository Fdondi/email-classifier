package com.emailtool.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One application the user sent (backfill or live mail); fields are package-private: only {@link ApplicationLearning} reads or writes them. */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** "L0001" for backfilled rows, null for ones found in live mail. */
    @Column(name = "ledger_id")
    String ledgerId;

    @Column(nullable = false)
    String company;

    /** {@link JobCandidateService#companyKey} of {@link #company}. */
    @Column(name = "company_key", nullable = false)
    String companyKey;

    String title;

    /** linkedin_easy_apply, direct_ats, direct_email, agency, referral, platform, unknown. */
    @Column(nullable = false)
    String channel;

    /** employer, consultancy, agency, unknown. */
    @Column(name = "company_type", nullable = false)
    String companyType;

    @Column(name = "applied_at", nullable = false)
    Instant appliedAt;

    /** Furthest stage, one of {@link ApplicationLearning#STAGES}. */
    @Column(nullable = false)
    String stage;

    /** rejected, offer, withdrawn; null while open. */
    String result;

    @Column(name = "reached_human", nullable = false)
    boolean reachedHuman;

    /** Known only from a human or interview mail, no trace of the application itself: left out of every rate. */
    @Column(name = "signal_only", nullable = false)
    boolean signalOnly;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
