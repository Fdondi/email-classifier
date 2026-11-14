package com.emailtool.tray;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Text for the tray tooltip and menu. Windows cuts a tooltip at 127 characters. */
final class TrayTooltip {

    static final int MAX_LENGTH = 127;
    private static final int NOTE_MAX = 30;
    private static final DateTimeFormatter HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_HOUR_MIN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_HOUR_MIN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private TrayTooltip() {}

    static String tooltip(TrayStatus s, Instant now, ZoneId zone) {
        String withCounts = String.join(
                "\n", "Email triage", lastRunLine(s, now, zone), lastCheckLine(s, now, zone), countsLine(s));
        if (withCounts.length() <= MAX_LENGTH) {
            return withCounts;
        }
        // Too long (e.g. "yesterday" dates): the counts are also in the right-click menu, so drop them first.
        String withoutCounts =
                String.join("\n", "Email triage", lastRunLine(s, now, zone), lastCheckLine(s, now, zone));
        return withoutCounts.length() <= MAX_LENGTH
                ? withoutCounts
                : withoutCounts.substring(0, MAX_LENGTH - 1) + "…";
    }

    static String lastRunLine(TrayStatus s, Instant now, ZoneId zone) {
        if (s.lastFullRun() == null) {
            return "Last full run: none yet";
        }
        return "Last full run: " + when(s.lastFullRun(), now, zone) + " · " + s.lastFullRunIngested() + " new";
    }

    static String lastCheckLine(TrayStatus s, Instant now, ZoneId zone) {
        if (s.lastCheck() == null) {
            return "Last check: none yet";
        }
        String line = "Last check: " + when(s.lastCheck(), now, zone);
        String note = shorten(s.lastCheckNote());
        return note.isEmpty() ? line : line + " · " + note;
    }

    static String countsLine(TrayStatus s) {
        return "Action " + s.actionNeeded() + " · Events " + s.events() + " · Jobs " + s.jobOffers();
    }

    static String weekLine(TrayStatus s) {
        return s.runsLast7Days() + " runs in 7 days · " + s.unclassified() + " not classified";
    }

    /** "14:11", "yesterday 14:11", "Mon 14:11" within the last week, otherwise "3 Nov 14:11". */
    static String when(Instant at, Instant now, ZoneId zone) {
        ZonedDateTime t = at.atZone(zone);
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate day = t.toLocalDate();
        if (day.equals(today)) {
            return HOUR_MIN.format(t);
        }
        if (day.equals(today.minusDays(1))) {
            return "yesterday " + HOUR_MIN.format(t);
        }
        if (day.isAfter(today.minusDays(7)) && day.isBefore(today)) {
            return DAY_HOUR_MIN.format(t);
        }
        return DATE_HOUR_MIN.format(t);
    }

    static String shorten(String note) {
        if (note == null) {
            return "";
        }
        String s = note.strip();
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.length() <= NOTE_MAX ? s : s.substring(0, NOTE_MAX - 1).stripTrailing() + "…";
    }
}
