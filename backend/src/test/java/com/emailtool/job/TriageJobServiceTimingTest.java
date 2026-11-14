package com.emailtool.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TriageJobServiceTimingTest {

    @Test
    void averageRoundsToNearestMillisecond() {
        assertEquals(0L, TriageJobService.averageMs(List.of()));
        assertEquals(10L, TriageJobService.averageMs(List.of(10L)));
        assertEquals(15L, TriageJobService.averageMs(List.of(10L, 20L)));
        assertEquals(13L, TriageJobService.averageMs(List.of(10L, 10L, 20L)));
    }

    @Test
    void percentileUsesNearestRank() {
        assertEquals(0L, TriageJobService.percentileMs(List.of(), 0.90));
        assertEquals(5L, TriageJobService.percentileMs(List.of(5L), 0.90));
        // 10 samples: ceil(0.9 * 10) - 1 = 8 → 9th value when sorted
        assertEquals(90L, TriageJobService.percentileMs(List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L), 0.90));
        assertEquals(100L, TriageJobService.percentileMs(List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L), 1.0));
    }

    @Test
    void percentileRejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> TriageJobService.percentileMs(List.of(1L), 0));
        assertThrows(IllegalArgumentException.class, () -> TriageJobService.percentileMs(List.of(1L), 1.1));
    }
}
