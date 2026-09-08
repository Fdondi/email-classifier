ALTER TABLE email ADD COLUMN unread INTEGER NOT NULL DEFAULT 1;

-- Previous hide-at-ingest used digest_visible=0 for read mail. Treat those as read and
-- restore inbox visibility so the UI Include-read checkbox can show them without a re-fetch.
UPDATE email SET unread = digest_visible;
UPDATE email SET digest_visible = 1;

CREATE INDEX idx_email_unread ON email(unread);
