-- Local user hide (UI preference). Distinct from digest_visible (IMAP archive/moved sync).
ALTER TABLE email ADD COLUMN user_hidden INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_email_user_hidden ON email(user_hidden);
