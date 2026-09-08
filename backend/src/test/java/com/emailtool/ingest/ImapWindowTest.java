package com.emailtool.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ImapWindowTest {

    private static final Instant NOW = Instant.parse("2026-09-07T18:00:00Z");

    private record Item(int id, Instant when) {}

    @Test
    void keepsEveryMessageFromLastDayEvenAboveCap() {
        List<Item> items = IntStream.rangeClosed(1, 120)
                .mapToObj(i -> new Item(i, NOW.minus(Duration.ofMinutes(i))))
                .toList();
        List<Item> chosen = ImapWindow.select(
                items, Item::when, NOW, Duration.ofHours(24), Duration.ofDays(14), 100);
        assertEquals(120, chosen.size());
    }

    @Test
    void fillsFromFourteenDaysWhenLastDayIsUnderCap() {
        List<Item> recent = List.of(new Item(1, NOW.minus(Duration.ofHours(2))));
        List<Item> older = IntStream.rangeClosed(2, 50)
                .mapToObj(i -> new Item(i, NOW.minus(Duration.ofDays(2)).minus(Duration.ofMinutes(i))))
                .toList();
        List<Item> tooOld = List.of(new Item(99, NOW.minus(Duration.ofDays(20))));
        List<Item> chosen = ImapWindow.select(
                concat(recent, older, tooOld), Item::when, NOW, Duration.ofHours(24), Duration.ofDays(14), 100);
        assertEquals(50, chosen.size());
        assertEquals(1, chosen.get(0).id());
        assertFalse(chosen.stream().anyMatch(item -> item.id() == 99));
    }

    @Test
    void stopsFillingAtMaxWhenOlderMailIsPlentiful() {
        List<Item> recent = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new Item(i, NOW.minus(Duration.ofHours(i))))
                .toList();
        List<Item> older = IntStream.rangeClosed(11, 200)
                .mapToObj(i -> new Item(i, NOW.minus(Duration.ofDays(3)).minus(Duration.ofMinutes(i))))
                .toList();
        List<Item> chosen = ImapWindow.select(
                concat(recent, older), Item::when, NOW, Duration.ofHours(24), Duration.ofDays(14), 100);
        assertEquals(100, chosen.size());
        assertEquals(10, chosen.stream().filter(item -> item.id() <= 10).count());
        assertEquals(90, chosen.stream().filter(item -> item.id() > 10).count());
    }

    @SafeVarargs
    private static List<Item> concat(List<Item>... parts) {
        return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
    }
}
