package com.emailtool.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Last {@code alwaysInclude} is taken in full. If that is under {@code maxMessages},
 * older mail from {@code fillWindow} fills the remainder, newest first.
 */
public final class ImapWindow {

    private ImapWindow() {}

    public static <T> List<T> select(
            List<T> items,
            Function<T, Instant> timestamp,
            Instant now,
            Duration alwaysInclude,
            Duration fillWindow,
            int maxMessages) {
        Instant recentSince = now.minus(alwaysInclude);
        Instant fillSince = now.minus(fillWindow);
        Comparator<T> newestFirst = Comparator.comparing(timestamp).reversed();
        List<T> recent = items.stream()
                .filter(item -> !timestamp.apply(item).isBefore(recentSince))
                .sorted(newestFirst)
                .toList();
        List<T> older = items.stream()
                .filter(item -> timestamp.apply(item).isBefore(recentSince)
                        && !timestamp.apply(item).isBefore(fillSince))
                .sorted(newestFirst)
                .toList();
        int room = Math.max(0, maxMessages - recent.size());
        List<T> chosen = new ArrayList<>(recent);
        int take = Math.min(room, older.size());
        chosen.addAll(older.subList(0, take));
        return List.copyOf(chosen);
    }
}
