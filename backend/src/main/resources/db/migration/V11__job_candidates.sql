-- Job postings found in JOB_OFFERS mail; the best are sent to letter-writer's Firestore `opportunities`.
CREATE TABLE job_posting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email_id INTEGER NOT NULL,
    canonical_key TEXT NOT NULL UNIQUE,
    url TEXT NOT NULL,
    link_url TEXT NOT NULL,
    title TEXT,
    company TEXT,
    location TEXT,
    summary TEXT,
    reason TEXT,
    interest_score INTEGER NOT NULL,
    found_at TEXT NOT NULL,
    status TEXT NOT NULL,
    sent_at TEXT,
    opportunity_id TEXT,
    capture TEXT,
    error TEXT
);

CREATE INDEX idx_job_posting_status ON job_posting(status);

-- JOB_OFFERS mail already read for postings (so each mail costs one LLM call).
CREATE TABLE job_mail_scan (
    email_id INTEGER PRIMARY KEY,
    scanned_at TEXT NOT NULL,
    postings INTEGER NOT NULL,
    error TEXT
);
