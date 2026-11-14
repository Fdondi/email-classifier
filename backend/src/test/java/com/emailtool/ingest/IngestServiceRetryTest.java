package com.emailtool.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestServiceRetryTest {

    private static final List<Duration> WAITS = List.of(Duration.ofSeconds(10), Duration.ofSeconds(30));

    @Test
    void returnsAfterTransientFailures() {
        int[] calls = {0};
        List<Duration> slept = new ArrayList<>();
        String result = IngestService.retrying(() -> {
            if (++calls[0] < 3) {
                throw new IllegalStateException("connection reset");
            }
            return "ok";
        }, WAITS, slept::add);
        assertEquals("ok", result);
        assertEquals(3, calls[0]);
        assertEquals(WAITS, slept);
    }

    @Test
    void givesUpAfterAllRetries() {
        int[] calls = {0};
        List<Duration> slept = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> IngestService.retrying(() -> {
            calls[0]++;
            throw new IllegalStateException("down");
        }, WAITS, slept::add));
        assertEquals(3, calls[0]);
        assertEquals(WAITS, slept);
    }
}
