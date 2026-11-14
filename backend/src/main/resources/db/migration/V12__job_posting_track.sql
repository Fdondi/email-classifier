-- The career track (app.jobs.tracks) a posting counts for; NULL = the general list.
ALTER TABLE job_posting ADD COLUMN track TEXT;
