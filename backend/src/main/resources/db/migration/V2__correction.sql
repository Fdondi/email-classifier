CREATE TABLE correction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email_id INTEGER NOT NULL REFERENCES email(id),
    field TEXT NOT NULL,
    predicted TEXT NOT NULL,
    corrected TEXT NOT NULL,
    model_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    taxonomy_version INTEGER NOT NULL,
    classification_source TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_correction_email_id ON correction(email_id);
CREATE INDEX idx_correction_created_at ON correction(created_at);
