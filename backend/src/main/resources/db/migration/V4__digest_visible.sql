ALTER TABLE email ADD COLUMN digest_visible INTEGER NOT NULL DEFAULT 1;
CREATE INDEX idx_email_digest_visible ON email(digest_visible);
