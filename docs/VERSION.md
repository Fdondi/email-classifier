0.6.5 - ASCII-only free-backend-port.ps1 so Windows PowerShell 5.1 can parse it.
0.6.4 - Run backend in Maven's JVM so Ctrl+C releases 8080; script to kill a leftover email-tool listener.
0.6.3 - Wait for VRAM after lms unload; skip huge LLM rank batches that crash Gemma and keep the GPU busy.
0.6.2 - Open Gmail via X-GM-MSGID #all/hex deep links by default; local body is opt-in preview.
0.6.1 - Verify triage script: unload, run job, assert LLM ready and meaningful classify counts.
0.6.0 - Run triage returns live progress (phase, downloaded, classified) while the job runs.
0.6.0 - Hide mail locally (user_hidden); recoverable Hidden tab; distinct from IMAP digest_visible.
0.6.0 - Detect actually loaded LM Studio models (not merely installed); collapse all-mail triage failures to one line.
0.5.0 - Show VERSION.md build versions for frontend (ui) and backend (api) in the nav.
0.4.1 - Triage downloads and rates all INBOX mail; Include read filters the digest UI; archived stays excluded.
0.4.0 - Open in Gmail from email detail via RFC822 Message-ID search link.
0.4.0 - Default IMAP triage to unread only (Include read toggle); exclude archived/all-mail folders; hide read/archived from digest on sync.
0.3.3 - Add a Runs tab with persisted triage history (start, duration, counts, errors).
0.3.2 - Nightly starts the backend; requeue list-unsubscribe rule guesses and FALLBACK for LLM; unsubscribe is a weak signal only.
0.3.1 - Do not hold the SQLite connection across IMAP or GPU load; corrections can run during triage.
0.3.0 - Load Gemma for triage when the GPU is free; unload afterward if this run loaded it.
0.2.0 - Compact digest email rows to two lines (subject+pill; from/date/summary).
0.1.9 - Category reassignment UI; persist corrections for training; stop List-Unsubscribe forcing newsletter.
0.1.8 - Show IMAP/LLM wiring in an info popup instead of a "Downloading mail" banner.
0.1.7 - Collapse empty digest categories to a single "Category: nothing." line.
0.1.6 - Restore broken categoryLabel export so the Angular digest compiles.
0.1.5 - Hide empty quarantine; drop synthetic testdata emails from the IMAP digest.
0.1.4 - IMAP always includes last 24h then fills to 100 from 14 days; Gemma JSON schema + captured-output test.
0.1.3 - Nightly wake: Gigabyte RTC-from-off notes, hibernate after success, skip if already ran today.
0.1.2 - Nightly Windows task: start LM Studio, load Gemma 4, run triage; longer LLM timeout.
0.1.1 - IMAP mail download and llama.cpp/LM Studio OpenAI-compatible classifier; Thunderbird is not used.
0.1.0 - Initial phases 1–4: Maildir ingest, normalize/sanitize, rule engine, Ollama classifier, read-only Angular digest UI.
