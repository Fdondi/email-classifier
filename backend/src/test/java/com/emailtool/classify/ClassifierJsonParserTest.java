package com.emailtool.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class ClassifierJsonParserTest {

    private final ClassifierJsonParser parser =
            new ClassifierJsonParser(new ObjectMapper().registerModule(new JavaTimeModule()), new AppProperties());

    @Test
    void parsesSnakeCaseJson() {
        String json =
                """
                {"category":"EVENT","confidence":0.9,"summary":"Workshop tomorrow","interest_score":80,
                 "due_date":"2025-09-08","injection_suspected":false,"language":"en"}
                """;
        ClassifierJson parsed = parser.parse(json).orElseThrow();
        assertEquals(Category.EVENT, parsed.category());
        assertEquals(false, parsed.actionNeeded());
        assertEquals(80, parsed.interestScore());
        assertEquals("2025-09-08", parsed.dueDate().toString());
        assertEquals(false, parsed.injectionSuspected());
    }

    @Test
    void rejectsUnknownCategory() {
        assertTrue(parser.parse("{\"category\":\"NOT_A_CATEGORY\",\"confidence\":1}").isEmpty());
    }

    @Test
    void truncatesSummary() {
        String summary = "x".repeat(500);
        ClassifierJson parsed = parser.parse(
                        "{\"category\":\"OTHER\",\"confidence\":0.1,\"summary\":\"" + summary + "\"}")
                .orElseThrow();
        assertEquals(200, parsed.summary().length());
    }

    @Test
    void parsesJsonInsideMarkdownFence() {
        String fenced =
                """
                ```json
                {"category":"SPAM","confidence":0.8,"summary":"Unsolicited offer","interest_score":5,
                 "due_date":null,"injection_suspected":false,"language":"en"}
                ```
                """;
        ClassifierJson parsed = parser.parse(fenced).orElseThrow();
        assertEquals(Category.SPAM, parsed.category());
    }

    @Test
    void defaultsActionNeededFromCategoryWhenFieldMissing() {
        ClassifierJson pay = parser.parse(
                        "{\"category\":\"ACTION_NEEDED_PAY\",\"confidence\":0.9,\"summary\":\"Invoice\"}")
                .orElseThrow();
        assertEquals(true, pay.actionNeeded());
        ClassifierJson event = parser.parse(
                        "{\"category\":\"EVENT\",\"confidence\":0.9,\"summary\":\"Talk\"}")
                .orElseThrow();
        assertEquals(false, event.actionNeeded());
    }

    @Test
    void honorsExplicitActionNeededIndependentOfCategory() {
        ClassifierJson parsed = parser.parse(
                        "{\"category\":\"EVENT\",\"confidence\":0.9,\"summary\":\"RSVP\",\"action_needed\":true}")
                .orElseThrow();
        assertEquals(Category.EVENT, parsed.category());
        assertEquals(true, parsed.actionNeeded());
    }
}
