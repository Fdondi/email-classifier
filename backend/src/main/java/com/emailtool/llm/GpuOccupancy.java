package com.emailtool.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class GpuOccupancy {

    private final boolean probeWorked;
    private final int freeMib;
    private final int usedMib;
    private final List<String> foreignProcesses;

    GpuOccupancy(boolean probeWorked, int freeMib, int usedMib, List<String> foreignProcesses) {
        this.probeWorked = probeWorked;
        this.freeMib = freeMib;
        this.usedMib = usedMib;
        this.foreignProcesses = List.copyOf(foreignProcesses);
    }

    public static GpuOccupancy unavailable(String detail) {
        return new GpuOccupancy(false, 0, 0, List.of(detail));
    }

    public static Optional<GpuOccupancy> parse(String gpuCsv, String computeCsv) {
        if (gpuCsv == null || gpuCsv.isBlank()) {
            return Optional.empty();
        }
        int bestFree = -1;
        int usedForBest = 0;
        for (String line : gpuCsv.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).startsWith("memory")) {
                continue;
            }
            String[] parts = trimmed.split(",");
            if (parts.length < 3) {
                continue;
            }
            Integer used = parseMib(parts[0]);
            Integer free = parseMib(parts[2]);
            if (used == null || free == null) {
                continue;
            }
            if (free > bestFree) {
                bestFree = free;
                usedForBest = used;
            }
        }
        if (bestFree < 0) {
            return Optional.empty();
        }
        return Optional.of(new GpuOccupancy(true, bestFree, usedForBest, foreignCompute(computeCsv)));
    }

    public boolean probeWorked() {
        return probeWorked;
    }

    public int freeMib() {
        return freeMib;
    }

    public boolean tooLittleFree(int minFreeMib) {
        return probeWorked && freeMib < minFreeMib;
    }

    public boolean hasForeignCompute() {
        return probeWorked && !foreignProcesses.isEmpty();
    }

    public String busyReason(int minFreeMib) {
        if (hasForeignCompute()) {
            return "GPU is in use by " + String.join(", ", foreignProcesses);
        }
        if (tooLittleFree(minFreeMib)) {
            return "GPU has " + freeMib + " MiB free (" + usedMib + " MiB used); need " + minFreeMib
                    + " MiB free to load the chat model";
        }
        return null;
    }

    static boolean isOwnProcess(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("lmstudio")
                || lower.contains("lm-studio")
                || lower.contains("lms.exe")
                || lower.contains("llama");
    }

    private static List<String> foreignCompute(String computeCsv) {
        List<String> foreign = new ArrayList<>();
        if (computeCsv == null || computeCsv.isBlank()) {
            return foreign;
        }
        for (String line : computeCsv.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).startsWith("pid")) {
                continue;
            }
            String[] parts = trimmed.split(",", 3);
            String name = parts.length >= 2 ? parts[1].trim() : trimmed;
            if (name.isEmpty() || isOwnProcess(name)) {
                continue;
            }
            foreign.add(name);
        }
        return foreign;
    }

    private static Integer parseMib(String raw) {
        String digits = raw.trim().replace("MiB", "").replace("mib", "").trim();
        if (digits.isEmpty() || digits.equalsIgnoreCase("N/A") || digits.equals("[")) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
