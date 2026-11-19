package com.emailtool.tray;

import java.time.Instant;

/** What the tray icon shows: the last full run, the last check, and a few counts. */
public record TrayStatus(
        Health health,
        /** Newest run with outcome OK (null if none). */
        Instant lastFullRun,
        int lastFullRunIngested,
        int lastFullRunClassified,
        /** Newest of: a scheduled check that did nothing (checks.log) or a run that started (null if none). */
        Instant lastCheck,
        String lastCheckNote,
        long actionNeeded,
        long events,
        long jobOffers,
        long unclassified,
        int runsLast7Days,
        /** A triage run is in progress: the backend job, or the nightly script loading the model. */
        boolean running,
        /** Next scheduled check from Task Scheduler (null if unknown or the tasks are not installed). */
        Instant nextCheck) {

    public enum Health {
        /** A full run finished in the last 12 hours and nothing newer failed. */
        OK,
        /** No full run yet, or the last one is older than 12 hours. */
        STALE,
        /** The newest run did not finish cleanly. */
        FAILING
    }
}
