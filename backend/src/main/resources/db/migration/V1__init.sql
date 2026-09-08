-- taxonomy version 1
CREATE TABLE email (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id TEXT,
    source_path TEXT NOT NULL,
    ingested_at TEXT NOT NULL,
    from_address TEXT,
    from_display_name TEXT,
    to_addresses TEXT,
    subject TEXT,
    date_sent TEXT,
    list_unsubscribe TEXT,
    authentication_results TEXT,
    dkim_result TEXT,
    spf_result TEXT,
    dmarc_result TEXT,
    raw_mime BLOB,
    text_plain TEXT,
    html_sanitized TEXT,
    language TEXT,
    charset_original TEXT,
    category TEXT,
    confidence REAL,
    summary TEXT,
    interest_score INTEGER,
    due_date TEXT,
    injection_suspected INTEGER NOT NULL DEFAULT 0,
    classification_source TEXT,
    model_version TEXT,
    prompt_version TEXT,
    taxonomy_version INTEGER,
    needs_manual_review INTEGER NOT NULL DEFAULT 0,
    classified_at TEXT,
    blended_score REAL,
    batch_rank INTEGER,
    ollama_error TEXT,
    images_loaded INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_email_message_id ON email(message_id) WHERE message_id IS NOT NULL;
CREATE INDEX idx_email_source_path ON email(source_path);
CREATE INDEX idx_email_category ON email(category);

CREATE TABLE ingested_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    mtime_millis INTEGER NOT NULL,
    message_id TEXT,
    ingested_at TEXT NOT NULL
);

CREATE TABLE email_remote_resource (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email_id INTEGER NOT NULL REFERENCES email(id),
    original_url TEXT NOT NULL,
    resource_index INTEGER NOT NULL,
    content_type TEXT,
    fetched_at TEXT,
    cached_bytes BLOB,
    UNIQUE(email_id, resource_index)
);

CREATE TABLE sender_prior (
    from_address TEXT PRIMARY KEY,
    open_rate REAL NOT NULL DEFAULT 0.5,
    reply_rate REAL NOT NULL DEFAULT 0.5,
    time_to_archive_ms INTEGER,
    sample_count INTEGER NOT NULL DEFAULT 0
);
