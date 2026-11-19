package com.emailtool.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TrayTooltipTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    // Monday 9 Nov 2026, 15:00 in Rome (14:00 UTC)
    private static final Instant NOW = Instant.parse("2026-11-09T14:00:00Z");

    private static final Instant NEXT = Instant.parse("2026-11-09T14:15:00Z");

    private static TrayStatus status(Instant lastRun, Instant lastCheck, String note) {
        return new TrayStatus(TrayStatus.Health.OK, lastRun, 87, 80, lastCheck, note, 52, 3, 2, 4, 5, false, NEXT);
    }

    @Test
    void tooltipListsLastRunLastCheckNextCheckAndCounts() {
        TrayStatus s = status(Instant.parse("2026-11-09T13:11:00Z"), Instant.parse("2026-11-09T14:00:00Z"), "Too soon.");
        assertEquals(
                "Email triage\nLast full run: 14:11 · 87 new\nLast check: 15:00 · Too soon\nNext check: 15:15\nAction 52 · Events 3 · Jobs 2",
                TrayTooltip.tooltip(s, NOW, ZONE));
    }

    @Test
    void runningShowsInTheTitleAndKeepsTheNextCheck() {
        TrayStatus s = new TrayStatus(TrayStatus.Health.OK, NOW, 87, 80, NOW, "Too soon: last success 14:11, runs again after 6 h.", 52, 3, 2, 4, 5, true, NEXT);
        String text = TrayTooltip.tooltip(s, NOW, ZONE);
        assertTrue(text.startsWith("Email triage · running\n"));
        assertTrue(text.contains("Next check: 15:15"));
        assertTrue(text.length() <= TrayTooltip.MAX_LENGTH);
    }

    @Test
    void longDatesDropTheCountsLineInsteadOfCuttingIt() {
        TrayStatus s = status(
                Instant.parse("2026-11-08T22:30:00Z"), Instant.parse("2026-11-08T22:45:00Z"), "Waiting: GPU in use by game.exe");
        String text = TrayTooltip.tooltip(s, NOW, ZONE);
        assertTrue(text.length() <= TrayTooltip.MAX_LENGTH);
        assertTrue(text.contains("Last full run: yesterday 23:30"));
        assertTrue(text.contains("Next check: 15:15"));
        assertTrue(!text.contains("Action"));
    }

    @Test
    void tooltipNeverExceedsWindowsLimit() {
        TrayStatus s = status(NOW, NOW, "x".repeat(500));
        assertTrue(TrayTooltip.tooltip(s, NOW, ZONE).length() <= TrayTooltip.MAX_LENGTH);
    }

    @Test
    void noRunsYet() {
        TrayStatus s = new TrayStatus(TrayStatus.Health.STALE, null, 0, 0, null, "", 0, 0, 0, 0, 0, false, null);
        String text = TrayTooltip.tooltip(s, NOW, ZONE);
        assertTrue(text.contains("Last full run: none yet"));
        assertTrue(text.contains("Last check: none yet"));
        assertTrue(text.contains("Next check: unknown"));
    }

    @Test
    void overdueAfterTwelveHoursWithoutAFullRun() {
        assertTrue(!TrayTooltip.overdue(status(NOW.minus(Duration.ofHours(11)), NOW, ""), NOW));
        TrayStatus late = status(NOW.minus(Duration.ofHours(14)), NOW, "Waiting: GPU in use. ");
        assertTrue(TrayTooltip.overdue(late, NOW));
        assertEquals("Email triage has not run for 14 h", TrayTooltip.overdueTitle(late, NOW));
        assertTrue(TrayTooltip.overdueBody(late).startsWith("Waiting: GPU in use.\nRight-click"));
        TrayStatus never = new TrayStatus(TrayStatus.Health.STALE, null, 0, 0, null, null, 0, 0, 0, 0, 0, false, null);
        assertTrue(TrayTooltip.overdue(never, NOW));
        assertEquals("Email triage has not run yet", TrayTooltip.overdueTitle(never, NOW));
        assertTrue(TrayTooltip.overdueBody(never).startsWith("Right-click"));
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
