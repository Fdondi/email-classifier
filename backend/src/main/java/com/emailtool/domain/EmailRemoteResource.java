package com.emailtool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_remote_resource")
public class EmailRemoteResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "resource_index", nullable = false)
    private int resourceIndex;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "cached_bytes")
    private byte[] cachedBytes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public int getResourceIndex() {
        return resourceIndex;
    }

    public void setResourceIndex(int resourceIndex) {
        this.resourceIndex = resourceIndex;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public byte[] getCachedBytes() {
        return cachedBytes;
    }

    public void setCachedBytes(byte[] cachedBytes) {
        this.cachedBytes = cachedBytes;
    }
}
