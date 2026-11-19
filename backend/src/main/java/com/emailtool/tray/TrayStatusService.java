package com.emailtool.tray;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.TriageRun;
import com.emailtool.domain.TriageRunOutcome;
import com.emailtool.job.TriageJobService;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.TriageRunRepository;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class TrayStatusService {

    // Also when the tray warns: scheduled runs wait for a free GPU (never CPU), so a long wait needs the user.
    static final Duration STALE_AFTER = Duration.ofHours(12);

    private final TriageRunRepository runRepository;
    private final EmailRepository emailRepository;
    private final AppProperties properties;
    private final TriageJobService triageJobService;

    public TrayStatusService(
            TriageRunRepository runRepository,
            EmailRepository emailRepository,
            AppProperties properties,
            TriageJobService triageJobService) {
        this.runRepository = runRepository;
        this.emailRepository = emailRepository;
        this.properties = properties;
        this.triageJobService = triageJobService;
    }

    public TrayStatus current() {
        return current(Instant.now());
    }

    TrayStatus current(Instant now) {
        List<TriageRun> runs = runRepository.findAllByOrderByStartedAtDesc();
        TriageRun latest = runs.isEmpty() ? null : runs.get(0);
        TriageRun lastOk = runs.stream()
                .filter(r -> r.getOutcome() == TriageRunOutcome.OK)
                .findFirst()
                .orElse(null);
        int runsLast7Days = (int) runs.stream()
                .filter(r -> r.getStartedAt().isAfter(now.minus(Duration.ofDays(7))))
                .count();

        Optional<CheckEntry> logged = lastCheck(Path.of(properties.getNightlyDir(), "checks.log"));
        Instant lastCheck = null;
        String note = "";
        if (logged.isPresent()) {
            lastCheck = logged.get().at();
            note = logged.get().note();
        }
        if (latest != null && (lastCheck == null || latest.getStartedAt().isAfter(lastCheck))) {
            lastCheck = latest.getStartedAt();
            note = "ran, " + latest.getOutcome().name().toLowerCase();
        }

        // The script's success marker wins when newer: it counts a run with some LLM results as a success
        // (and stops rerunning for 6 h), while the database calls the same run PARTIAL.
        Instant marked = lastSuccessMarker(Path.of(properties.getNightlyDir(), "last-success.txt")).orElse(null);
        Instant lastFull = lastOk == null ? null : lastOk.getFinishedAt();
        TriageRun lastFullRun = lastOk;
        if (marked != null && (lastFull == null || marked.isAfter(lastFull))) {
            lastFull = marked;
            Instant markedAt = marked;
            lastFullRun = runs.stream()
                    .filter(r -> !r.getStartedAt().isAfter(markedAt))
                    .findFirst()
                    .orElse(null);
        }

        // Runs every minute: SQL counts, not DigestService.digest(), which loads every mail body.
        Object[] totals = emailRepository.unreadDigestTotals().get(0);
        return new TrayStatus(
                health(latest, lastFull, now),
                lastFull,
                lastFullRun == null ? 0 : lastFullRun.getIngested(),
                lastFullRun == null ? 0 : lastFullRun.getClassifiedByRule() + lastFullRun.getClassifiedByLlm(),
                lastCheck,
                note,
                ((Number) totals[0]).longValue(),
                ((Number) totals[1]).longValue(),
                ((Number) totals[2]).longValue(),
                emailRepository.countByCategoryIsNull(),
                runsLast7Days,
                running(Path.of(properties.getNightlyDir(), "running.txt"), now),
                nextCheck(now));
    }

    private boolean running(Path marker, Instant now) {
        return triageJobService.progress().running() || markerFresh(marker, now);
    }

    /**
     * run-triage.ps1 keeps results/nightly/running.txt while a run is going (it also covers the model load,
     * before the backend job starts). A crashed script leaves it behind, so it counts only for 8 h, the
     * scheduled task's own time limit.
     */
    static boolean markerFresh(Path marker, Instant now) {
        try {
            return Files.getLastModifiedTime(marker).toInstant().isAfter(now.minus(Duration.ofHours(8)));
        } catch (IOException e) {
            return false;
        }
    }

    private Instant cachedNext;

    /** Asks Task Scheduler only when the cached time has passed: about once per check, not once per minute. */
    private synchronized Instant nextCheck(Instant now) {
        if (cachedNext == null || !cachedNext.isAfter(now)) {
            cachedNext = queryNextCheck().orElse(null);
        }
        return cachedNext;
    }

    private static Optional<Instant> queryNextCheck() {
        String script = "'EmailToolTriageCatchUp','EmailToolNightlyTriage' | ForEach-Object { "
                + "Get-ScheduledTaskInfo -TaskName $_ -ErrorAction SilentlyContinue } "
                + "| Where-Object { $_.NextRunTime } | Sort-Object NextRunTime | Select-Object -First 1 "
                + "| ForEach-Object { $_.NextRunTime.ToUniversalTime().ToString('o') }";
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            return parseNextCheck(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static Optional<Instant> parseNextCheck(String output) {
        try {
            return Optional.of(OffsetDateTime.parse(output.strip()).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static TrayStatus.Health health(TriageRun latest, Instant lastFullRun, Instant now) {
        if (latest != null
                && latest.getOutcome() != TriageRunOutcome.OK
                && (lastFullRun == null || latest.getStartedAt().isAfter(lastFullRun))) {
            return TrayStatus.Health.FAILING;
        }
        if (lastFullRun == null || lastFullRun.isBefore(now.minus(STALE_AFTER))) {
            return TrayStatus.Health.STALE;
        }
        return TrayStatus.Health.OK;
    }

    /** results/nightly/last-success.txt holds one ISO-8601 timestamp, written by run-triage.ps1. */
    static Optional<Instant> lastSuccessMarker(Path file) {
        try {
            return Optional.of(OffsetDateTime.parse(Files.readString(file).strip().replace("﻿", "")).toInstant());
        } catch (IOException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    record CheckEntry(Instant at, String note) {}

    /**
     * The last line of results/nightly/checks.log, written by scripts/nightly/run-triage.ps1 each time
     * a scheduled check decides not to run. Format: {@code <ISO-8601 time> <message>}.
     */
    static Optional<CheckEntry> lastCheck(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            int tail = (int) Math.min(length, 4096);
            byte[] buf = new byte[tail];
            raf.seek(length - tail);
            raf.readFully(buf);
            String[] lines = new String(buf, StandardCharsets.UTF_8).split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                Optional<CheckEntry> entry = parseLine(lines[i]);
                if (entry.isPresent()) {
                    return entry;
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static Optional<CheckEntry> parseLine(String line) {
        String trimmed = line.strip().replace("﻿", "");
        int space = trimmed.indexOf(' ');
        if (space <= 0) {
            return Optional.empty();
        }
        try {
            Instant at = OffsetDateTime.parse(trimmed.substring(0, space)).toInstant();
            return Optional.of(new CheckEntry(at, trimmed.substring(space + 1).strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
