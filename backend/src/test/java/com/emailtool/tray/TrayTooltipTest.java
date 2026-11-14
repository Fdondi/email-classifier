package com.emailtool.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TrayTooltipTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    // Monday 9 Nov 2026, 15:00 in Rome (14:00 UTC)
    private static final Instant NOW = Instant.parse("2026-11-09T14:00:00Z");

    private static TrayStatus status(Instant lastRun, Instant lastCheck, String note) {
        return new TrayStatus(TrayStatus.Health.OK, lastRun, 87, 80, lastCheck, note, 52, 3, 2, 4, 5);
    }

    @Test
    void tooltipListsLastRunLastCheckAndCounts() {
        TrayStatus s = status(
                Instant.parse("2026-11-09T13:11:00Z"), Instant.parse("2026-11-09T14:00:00Z"), "Too soon: last success 14:11, runs again after 6 h.");
        assertEquals(
                "Email triage\nLast full run: 14:11 · 87 new\nLast check: 15:00 · Too soon: last success 14:11,…\nAction 52 · Events 3 · Jobs 2",
                TrayTooltip.tooltip(s, NOW, ZONE));
    }

    @Test
    void longDatesDropTheCountsLineInsteadOfCuttingIt() {
        TrayStatus s = status(
                Instant.parse("2026-11-08T22:30:00Z"), Instant.parse("2026-11-08T22:45:00Z"), "Waiting: GPU in use by game.exe");
        String text = TrayTooltip.tooltip(s, NOW, ZONE);
        assertTrue(text.length() <= TrayTooltip.MAX_LENGTH);
        assertTrue(text.contains("Last full run: yesterday 23:30"));
        assertTrue(!text.contains("Action"));
    }

    @Test
    void tooltipNeverExceedsWindowsLimit() {
        TrayStatus s = status(NOW, NOW, "x".repeat(500));
        assertTrue(TrayTooltip.tooltip(s, NOW, ZONE).length() <= TrayTooltip.MAX_LENGTH);
    }

    @Test
    void noRunsYet() {
        TrayStatus s = new TrayStatus(TrayStatus.Health.STALE, null, 0, 0, null, "", 0, 0, 0, 0, 0);
        String text = TrayTooltip.tooltip(s, NOW, ZONE);
        assertTrue(text.contains("Last full run: none yet"));
        assertTrue(text.contains("Last check: none yet"));
    }

    @Test
    void timeIsRelativeToToday() {
        assertEquals("03:12", TrayTooltip.when(Instant.parse("2026-11-09T02:12:00Z"), NOW, ZONE));
        assertEquals("yesterday 23:30", TrayTooltip.when(Instant.parse("2026-11-08T22:30:00Z"), NOW, ZONE));
        assertEquals("Fri 10:00", TrayTooltip.when(Instant.parse("2026-11-06T09:00:00Z"), NOW, ZONE));
        assertEquals("1 Nov 10:00", TrayTooltip.when(Instant.parse("2026-11-01T09:00:00Z"), NOW, ZONE));
    }

    @Test
    void shortenStripsTrailingDotsAndCaps() {
        assertEquals("Another run is in progress", TrayTooltip.shorten("Another run is in progress."));
        assertEquals("", TrayTooltip.shorten(null));
        assertEquals(30, TrayTooltip.shorten("y".repeat(80)).length());
    }
}
