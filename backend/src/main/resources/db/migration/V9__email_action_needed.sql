-- Action needed is independent of category (pay, event, interview, ...).
ALTER TABLE email ADD COLUMN action_needed INTEGER NOT NULL DEFAULT 0;
UPDATE email SET action_needed = 1 WHERE category IN (
    'ACTION_NEEDED_PAY',
    'ACTION_NEEDED_SIGN',
    'ACTION_NEEDED_OTHER',
    'JOB_INTERVIEWS'
);
CREATE INDEX idx_email_action_needed ON email(action_needed);
