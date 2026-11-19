package com.emailtool.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.domain.TriageRun;
import com.emailtool.domain.TriageRunOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrayStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T14:00:00Z");

    @TempDir
    Path dir;

    private static TriageRun run(TriageRunOutcome outcome, String started, String finished) {
        TriageRun r = new TriageRun();
        r.setOutcome(outcome);
        r.setStartedAt(Instant.parse(started));
        r.setFinishedAt(Instant.parse(finished));
        return r;
    }

    @Test
    void parsesPowerShellRoundTripTimestamp() {
        // Get-Date -Format o writes 7 fractional digits and a local offset
        Optional<TrayStatusService.CheckEntry> e = TrayStatusService.parseLine(
                "2026-11-09T13:00:02.5349906+01:00 Waiting: only 7377 MiB GPU memory free, Gemma needs 18000.");
        assertTrue(e.isPresent());
        assertEquals(Instant.parse("2026-11-09T12:00:02.534990600Z"), e.get().at());
        assertEquals("Waiting: only 7377 MiB GPU memory free, Gemma needs 18000.", e.get().note());
    }

    @Test
    void ignoresGarbageLines() {
        assertTrue(TrayStatusService.parseLine("").isEmpty());
        assertTrue(TrayStatusService.parseLine("no timestamp here").isEmpty());
    }

    @Test
    void lastCheckIsTheLastParsableLine() throws Exception {
        Path log = dir.resolve("checks.log");
        Files.writeString(
                log,
                "2026-11-09T11:00:03+01:00 Waiting: busy\n2026-11-09T14:15:00+01:00 Too soon: x\n\n");
        TrayStatusService.CheckEntry e = TrayStatusService.lastCheck(log).orElseThrow();
        assertEquals(Instant.parse("2026-11-09T13:15:00Z"), e.at());
        assertEquals("Too soon: x", e.note());
    }

    @Test
    void missingLogIsEmpty() {
        assertTrue(TrayStatusService.lastCheck(dir.resolve("nope.log")).isEmpty());
    }

    @Test
    void healthIsOkWhenRecentFullRunAndNothingNewerFailed() {
        TriageRun ok = run(TriageRunOutcome.OK, "2026-11-09T03:00:00Z", "2026-11-09T03:10:00Z");
        assertEquals(TrayStatus.Health.OK, TrayStatusService.health(ok, ok.getFinishedAt(), NOW));
    }

    @Test
    void healthIsStaleWithoutRecentFullRun() {
        Instant old = Instant.parse("2026-11-07T03:10:00Z");
        assertEquals(TrayStatus.Health.STALE, TrayStatusService.health(null, old, NOW));
        assertEquals(TrayStatus.Health.STALE, TrayStatusService.health(null, null, NOW));
    }

    @Test
    void healthIsFailingWhenNewestRunFailedAfterLastFullRun() {
        Instant lastFull = Instant.parse("2026-11-09T03:10:00Z");
        TriageRun failed = run(TriageRunOutcome.FAILED, "2026-11-09T09:00:00Z", "2026-11-09T09:01:00Z");
        assertEquals(TrayStatus.Health.FAILING, TrayStatusService.health(failed, lastFull, NOW));
    }

    @Test
    void aSuccessMarkerNewerThanAPartialRunClearsTheFailure() {
        // 14:01 run was PARTIAL, then the script marked success at 14:11
        TriageRun partial = run(TriageRunOutcome.PARTIAL, "2026-11-09T13:01:00Z", "2026-11-09T13:10:00Z");
        Instant marked = Instant.parse("2026-11-09T13:11:51Z");
        assertEquals(TrayStatus.Health.OK, TrayStatusService.health(partial, marked, NOW));
    }

    @Test
    void readsTheSuccessMarkerFile() throws Exception {
        Path file = dir.resolve("last-success.txt");
        Files.writeString(file, "2026-11-09T14:11:51.1204630+01:00\r\n");
        assertEquals(Instant.parse("2026-11-09T13:11:51.120463Z"), TrayStatusService.lastSuccessMarker(file).orElseThrow());
        assertTrue(TrayStatusService.lastSuccessMarker(dir.resolve("none.txt")).isEmpty());
    }

    @Test
    void parsesNextCheckFromTaskScheduler() {
        assertEquals(Instant.parse("2026-11-15T11:00:00Z"), TrayStatusService.parseNextCheck("2026-11-15T11:00:00.0000000Z\r\n").orElseThrow());
        assertTrue(TrayStatusService.parseNextCheck("").isEmpty());
        assertTrue(TrayStatusService.parseNextCheck("Get-ScheduledTaskInfo: not found").isEmpty());
    }

    @Test
    void runningMarkerCountsOnlyWhenFresh() throws Exception {
        Path marker = dir.resolve("running.txt");
        assertTrue(!TrayStatusService.markerFresh(marker, NOW));
        Files.writeString(marker, "x");
        Files.setLastModifiedTime(marker, FileTime.from(NOW.minus(Duration.ofHours(1))));
        assertTrue(TrayStatusService.markerFresh(marker, NOW));
        Files.setLastModifiedTime(marker, FileTime.from(NOW.minus(Duration.ofHours(9))));
        assertTrue(!TrayStatusService.markerFresh(marker, NOW));
    }
}
