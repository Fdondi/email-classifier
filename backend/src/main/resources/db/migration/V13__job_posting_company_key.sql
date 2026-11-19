-- Normalised company name (JobCandidateService.companyKey): one company is suggested once per 30 days.
ALTER TABLE job_posting ADD COLUMN company_key TEXT;
CREATE INDEX idx_job_posting_company_key ON job_posting (company_key, sent_at);
