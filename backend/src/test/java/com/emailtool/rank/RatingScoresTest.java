package com.emailtool.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.emailtool.domain.Email;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RatingScoresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    @Test
    void overdueAndTodayAreFullyUrgent() {
        assertEquals(1.0, RatingScores.inferredUrgency(TODAY.minusDays(2), TODAY));
        assertEquals(1.0, RatingScores.inferredUrgency(TODAY, TODAY));
    }

    @Test
    void interpolatesInsideTheFourteenDayWindow() {
        assertEquals(1.0 - 7 / 14.0, RatingScores.inferredUrgency(TODAY.plusDays(7), TODAY));
    }

    @Test
    void farFutureAndMissingDueDateAreZero() {
        assertEquals(0.0, RatingScores.inferredUrgency(TODAY.plusDays(14), TODAY));
        assertEquals(0.0, RatingScores.inferredUrgency(TODAY.plusDays(30), TODAY));
        assertEquals(0.0, RatingScores.inferredUrgency(null, TODAY));
    }

    @Test
    void userRatingOverridesInference() {
        Email email = new Email();
        email.setDueDate(TODAY);
        email.setBlendedScore(0.2);
        email.setRatingUrgency(0.4);
        email.setRatingImportance(0.9);
        assertEquals(0.4, RatingScores.urgency(email, TODAY));
        assertEquals(0.9, RatingScores.importance(email));
    }
}
