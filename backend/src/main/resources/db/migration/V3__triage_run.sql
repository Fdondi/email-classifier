CREATE TABLE triage_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    source TEXT NOT NULL,
    outcome TEXT NOT NULL,
    ingested INTEGER NOT NULL,
    skipped INTEGER NOT NULL,
    ingest_failed INTEGER NOT NULL,
    requeued INTEGER NOT NULL,
    classified_by_rule INTEGER NOT NULL,
    classified_by_llm INTEGER NOT NULL,
    fallback INTEGER NOT NULL,
    unclassified_left INTEGER NOT NULL,
    llm_ready INTEGER NOT NULL,
    errors TEXT
);

CREATE INDEX idx_triage_run_started_at ON triage_run(started_at);
