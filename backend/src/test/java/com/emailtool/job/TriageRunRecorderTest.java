package com.emailtool.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.emailtool.domain.TriageRunOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class TriageRunRecorderTest {

    @Test
    void emptyErrorsIsOk() {
        assertEquals(
                TriageRunOutcome.OK,
                TriageRunRecorder.outcome(new TriageJobService.TriageJobResult(0, 0, 0, 0, 0, 0, List.of())));
    }

    @Test
    void errorsWithWorkIsPartial() {
        assertEquals(
                TriageRunOutcome.PARTIAL,
                TriageRunRecorder.outcome(
                        new TriageJobService.TriageJobResult(0, 0, 0, 0, 4, 1, List.of("one failed"))));
    }

    @Test
    void errorsWithNoWorkIsFailed() {
        assertEquals(
                TriageRunOutcome.FAILED,
                TriageRunRecorder.outcome(
                        new TriageJobService.TriageJobResult(0, 0, 0, 0, 0, 0, List.of("GPU busy"))));
    }
}
