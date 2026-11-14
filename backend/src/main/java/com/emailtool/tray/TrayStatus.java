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
        int runsLast7Days) {

    public enum Health {
        /** A full run finished in the last 24 hours and nothing newer failed. */
        OK,
        /** No full run yet, or the last one is older than 24 hours. */
        STALE,
        /** The newest run did not finish cleanly. */
        FAILING
    }
}
