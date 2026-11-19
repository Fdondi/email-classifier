package com.emailtool.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "job_posting")
public class JobPosting {

    public enum Status {
        /** Found, not (yet) sent. */
        PENDING,
        /** Created in letter-writer as a proposal. */
        SENT,
        /** letter-writer already had this job (from the phone, the extension or an earlier mail). */
        ALREADY_IN_APP,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_id", nullable = false)
    private Long emailId;

    @Column(name = "canonical_key", nullable = false, unique = true)
    private String canonicalKey;

    /** As stored in letter-writer (tracking and sign-in tokens removed). */
    @Column(nullable = false)
    private String url;

    /** As it appeared in the mail; only used to fetch the page. */
    @Column(name = "link_url", nullable = false)
    private String linkUrl;

    private String title;
    private String company;

    /** {@link JobCandidateService#companyKey}: "" when the company is unknown, null only before it was filled in. */
    @Column(name = "company_key")
    private String companyKey;

    private String location;
    private String summary;
    private String reason;

    @Column(name = "interest_score", nullable = false)
    private int interestScore;

    @Column(name = "found_at", nullable = false)
    private Instant foundAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opportunity_id")
    private String opportunityId;

    /** "page text" when the posting was downloaded, else why only the link was sent. */
    private String capture;

    private String error;

    /** The configured career track the posting counts for (its own daily quota); null for the general list. */
    private String track;

    public Long getId() {
        return id;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public Long getEmailId() {
        return emailId;
    }

    public void setEmailId(Long emailId) {
        this.emailId = emailId;
    }

    public String getCanonicalKey() {
        return canonicalKey;
    }

    public void setCanonicalKey(String canonicalKey) {
        this.canonicalKey = canonicalKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getCompanyKey() {
        return companyKey;
    }

    public void setCompanyKey(String companyKey) {
        this.companyKey = companyKey;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getInterestScore() {
        return interestScore;
    }

    public void setInterestScore(int interestScore) {
        this.interestScore = interestScore;
    }

    public Instant getFoundAt() {
        return foundAt;
    }

    public void setFoundAt(Instant foundAt) {
        this.foundAt = foundAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(String opportunityId) {
        this.opportunityId = opportunityId;
    }

    public String getCapture() {
        return capture;
    }

    public void setCapture(String capture) {
        this.capture = capture;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
