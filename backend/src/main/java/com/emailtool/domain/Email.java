package com.emailtool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_display_name")
    private String fromDisplayName;

    @Column(name = "to_addresses")
    private String toAddresses;

    private String subject;

    @Column(name = "date_sent")
    private Instant dateSent;

    @Column(name = "list_unsubscribe")
    private String listUnsubscribe;

    @Column(name = "authentication_results")
    private String authenticationResults;

    @Column(name = "dkim_result")
    private String dkimResult;

    @Column(name = "spf_result")
    private String spfResult;

    @Column(name = "dmarc_result")
    private String dmarcResult;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "raw_mime")
    private byte[] rawMime;

    @Column(name = "text_plain")
    private String textPlain;

    @Column(name = "html_sanitized")
    private String htmlSanitized;

    private String language;

    @Column(name = "charset_original")
    private String charsetOriginal;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Double confidence;

    private String summary;

    @Column(name = "interest_score")
    private Integer interestScore;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "injection_suspected", nullable = false)
    private boolean injectionSuspected;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_source")
    private ClassificationSource classificationSource;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "taxonomy_version")
    private Integer taxonomyVersion;

    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "needs_manual_review", nullable = false)
    private boolean needsManualReview;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    @Column(name = "blended_score")
    private Double blendedScore;

    @Column(name = "batch_rank")
    private Integer batchRank;

    @Column(name = "ollama_error")
    private String ollamaError;

    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "images_loaded", nullable = false)
    private boolean imagesLoaded;

    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "digest_visible", nullable = false)
    private boolean digestVisible = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromDisplayName() {
        return fromDisplayName;
    }

    public void setFromDisplayName(String fromDisplayName) {
        this.fromDisplayName = fromDisplayName;
    }

    public String getToAddresses() {
        return toAddresses;
    }

    public void setToAddresses(String toAddresses) {
        this.toAddresses = toAddresses;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getDateSent() {
        return dateSent;
    }

    public void setDateSent(Instant dateSent) {
        this.dateSent = dateSent;
    }

    public String getListUnsubscribe() {
        return listUnsubscribe;
    }

    public void setListUnsubscribe(String listUnsubscribe) {
        this.listUnsubscribe = listUnsubscribe;
    }

    public String getAuthenticationResults() {
        return authenticationResults;
    }

    public void setAuthenticationResults(String authenticationResults) {
        this.authenticationResults = authenticationResults;
    }

    public String getDkimResult() {
        return dkimResult;
    }

    public void setDkimResult(String dkimResult) {
        this.dkimResult = dkimResult;
    }

    public String getSpfResult() {
        return spfResult;
    }

    public void setSpfResult(String spfResult) {
        this.spfResult = spfResult;
    }

    public String getDmarcResult() {
        return dmarcResult;
    }

    public void setDmarcResult(String dmarcResult) {
        this.dmarcResult = dmarcResult;
    }

    public byte[] getRawMime() {
        return rawMime;
    }

    public void setRawMime(byte[] rawMime) {
        this.rawMime = rawMime;
    }

    public String getTextPlain() {
        return textPlain;
    }

    public void setTextPlain(String textPlain) {
        this.textPlain = textPlain;
    }

    public String getHtmlSanitized() {
        return htmlSanitized;
    }

    public void setHtmlSanitized(String htmlSanitized) {
        this.htmlSanitized = htmlSanitized;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCharsetOriginal() {
        return charsetOriginal;
    }

    public void setCharsetOriginal(String charsetOriginal) {
        this.charsetOriginal = charsetOriginal;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getInterestScore() {
        return interestScore;
    }

    public void setInterestScore(Integer interestScore) {
        this.interestScore = interestScore;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isInjectionSuspected() {
        return injectionSuspected;
    }

    public void setInjectionSuspected(boolean injectionSuspected) {
        this.injectionSuspected = injectionSuspected;
    }

    public ClassificationSource getClassificationSource() {
        return classificationSource;
    }

    public void setClassificationSource(ClassificationSource classificationSource) {
        this.classificationSource = classificationSource;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Integer getTaxonomyVersion() {
        return taxonomyVersion;
    }

    public void setTaxonomyVersion(Integer taxonomyVersion) {
        this.taxonomyVersion = taxonomyVersion;
    }

    public boolean isNeedsManualReview() {
        return needsManualReview;
    }

    public void setNeedsManualReview(boolean needsManualReview) {
        this.needsManualReview = needsManualReview;
    }

    public Instant getClassifiedAt() {
        return classifiedAt;
    }

    public void setClassifiedAt(Instant classifiedAt) {
        this.classifiedAt = classifiedAt;
    }

    public Double getBlendedScore() {
        return blendedScore;
    }

    public void setBlendedScore(Double blendedScore) {
        this.blendedScore = blendedScore;
    }

    public Integer getBatchRank() {
        return batchRank;
    }

    public void setBatchRank(Integer batchRank) {
        this.batchRank = batchRank;
    }

    public String getOllamaError() {
        return ollamaError;
    }

    public void setOllamaError(String ollamaError) {
        this.ollamaError = ollamaError;
    }

    public boolean isImagesLoaded() {
        return imagesLoaded;
    }

    public void setImagesLoaded(boolean imagesLoaded) {
        this.imagesLoaded = imagesLoaded;
    }

    public boolean isDigestVisible() {
        return digestVisible;
    }

    public void setDigestVisible(boolean digestVisible) {
        this.digestVisible = digestVisible;
    }
}
