-- Applications the user sent: the Gmail backfill since 2025, then kept current from outcome mail.
-- Reply rates per channel, company type and company are computed from these rows.
CREATE TABLE job_application (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ledger_id TEXT,
    company TEXT NOT NULL,
    company_key TEXT NOT NULL,
    title TEXT,
    channel TEXT NOT NULL,
    company_type TEXT NOT NULL,
    applied_at TEXT NOT NULL,
    stage TEXT NOT NULL,
    result TEXT,
    reached_human INTEGER NOT NULL,
    signal_only INTEGER NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_job_application_company_key ON job_application(company_key);

-- Every mail read for an application outcome (backfill events included), so none counts twice.
-- `change` says what the mail changed in the learning data; the UI lists those rows.
CREATE TABLE job_application_mail (
    mail_id TEXT PRIMARY KEY,
    email_id INTEGER,
    application_id INTEGER,
    at TEXT NOT NULL,
    kind TEXT NOT NULL,
    summary TEXT,
    change TEXT
);

CREATE INDEX idx_job_application_mail_at ON job_application_mail(at);

-- employer / consultancy / agency (an agency that does not name its client), as the model read the posting.
ALTER TABLE job_posting ADD COLUMN company_type TEXT;
