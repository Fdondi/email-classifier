package com.emailtool.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TriageJobServiceErrorCollapseTest {

    @Test
    void collapsesWhenEveryLlmAttemptFailsWithSameReason() {
        List<String> collapsed = TriageJobService.collapsedErrors(
                List.of("GPU busy"),
                List.of(
                        "Email 1 left for manual review (LLM unavailable or invalid JSON)",
                        "Email 2 left for manual review (LLM unavailable or invalid JSON)",
                        "Email 3 left for manual review (LLM unavailable or invalid JSON)"),
                3);
        assertEquals(
                List.of("GPU busy", "All 3 emails failed: left for manual review (LLM unavailable or invalid JSON)"),
                collapsed);
    }

    @Test
    void keepsPerEmailLinesWhenOnlySomeFail() {
        List<String> collapsed = TriageJobService.collapsedErrors(
                List.of(),
                List.of("Email 9 left for manual review (LLM unavailable or invalid JSON)"),
                4);
        assertEquals(
                List.of("Email 9 left for manual review (LLM unavailable or invalid JSON)"), collapsed);
    }

    @Test
    void collapsesMixedExceptionMessagesOnlyWhenIdentical() {
        List<String> collapsed = TriageJobService.collapsedErrors(
                List.of(),
                List.of("Email 1: connection reset", "Email 2: connection reset"),
                2);
        assertEquals(List.of("All 2 emails failed: connection reset"), collapsed);
    }
}
