package com.emailtool.rank;

import com.emailtool.domain.Email;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Dual-axis ratings are 0-1. A user drag stores explicit values. Unrated mail uses
 * due date (urgency) and blended score (importance) so we do not invent scores.
 *
 * <p>Inferred urgency: overdue/today = 1, linearly down to 0 at {@link #URGENCY_HORIZON_DAYS}
 * (the IMAP 14-day window). No due date = 0.
 */
public final class RatingScores {

    public static final int URGENCY_HORIZON_DAYS = 14;

    private RatingScores() {}

    public static double urgency(Email email, LocalDate today) {
        if (email.getRatingUrgency() != null) {
            return clamp01(email.getRatingUrgency());
        }
        return inferredUrgency(email.getDueDate(), today);
    }

    public static double importance(Email email) {
        if (email.getRatingImportance() != null) {
            return clamp01(email.getRatingImportance());
        }
        if (email.getBlendedScore() == null) {
            return 0;
        }
        return clamp01(email.getBlendedScore());
    }

    public static double inferredUrgency(LocalDate due, LocalDate today) {
        if (due == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        if (days <= 0) {
            return 1;
        }
        if (days >= URGENCY_HORIZON_DAYS) {
            return 0;
        }
        return 1.0 - days / (double) URGENCY_HORIZON_DAYS;
    }

    public static double clamp01(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
