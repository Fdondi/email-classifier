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
