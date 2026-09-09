package com.emailtool.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.domain.Email;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DigestServiceExpiredOrderTest {

    @Test
    void dueTodayIsNotExpired() {
        Email today = email(LocalDate.now(), 50.0);
        assertFalse(DigestService.expired(today));
    }

    @Test
    void pastDueIsExpired() {
        Email past = email(LocalDate.now().minusDays(1), 50.0);
        assertTrue(DigestService.expired(past));
    }

    @Test
    void expiredSortsAheadOfHigherImportance() {
        Email expired = email(LocalDate.now().minusDays(3), 10.0);
        Email upcoming = email(LocalDate.now().plusDays(2), 90.0);
        Email noDue = email(null, 99.0);
        List<Email> sorted = Stream.of(noDue, upcoming, expired)
                .sorted(DigestService.BY_IMPORTANCE)
                .toList();
        assertEquals(List.of(expired, noDue, upcoming), sorted);
    }

    @Test
    void dueDateOrderStillAppliesAmongExpired() {
        Email older = email(LocalDate.now().minusDays(10), 1.0);
        Email newer = email(LocalDate.now().minusDays(1), 1.0);
        Email upcoming = email(LocalDate.now().plusDays(1), 1.0);
        List<Email> sorted = Stream.of(upcoming, newer, older)
                .sorted(DigestService.BY_DUE_THEN_SENT)
                .toList();
        assertEquals(List.of(older, newer, upcoming), sorted);
    }

    @Test
    void userUrgencyRatingSortsAheadOfLaterDueDate() {
        Email laterDue = email(LocalDate.now().plusDays(1), 0.2);
        Email userUrgent = email(LocalDate.now().plusDays(10), 0.2);
        userUrgent.setRatingUrgency(0.95);
        List<Email> sorted = Stream.of(laterDue, userUrgent)
                .sorted(DigestService.BY_DUAL_URGENCY)
                .toList();
        assertEquals(List.of(userUrgent, laterDue), sorted);
    }

    @Test
    void userImportanceRatingSortsAheadOfHigherBlendedScore() {
        Email highBlended = email(LocalDate.now().plusDays(2), 0.9);
        Email userImportant = email(LocalDate.now().plusDays(2), 0.1);
        userImportant.setRatingImportance(0.95);
        List<Email> sorted = Stream.of(highBlended, userImportant)
                .sorted(DigestService.BY_DUAL_IMPORTANCE)
                .toList();
        assertEquals(List.of(userImportant, highBlended), sorted);
    }

    @Test
    void listRankOverridesDueDateOrder() {
        Email sooner = email(LocalDate.now().plusDays(1), 0.2);
        Email later = email(LocalDate.now().plusDays(10), 0.2);
        later.setListRank(0);
        sooner.setListRank(1);
        List<Email> sorted = Stream.of(sooner, later)
                .sorted(DigestService.BY_DUE_THEN_SENT)
                .toList();
        assertEquals(List.of(later, sooner), sorted);
    }

    private static Email email(LocalDate due, double blended) {
        Email email = new Email();
        email.setDueDate(due);
        email.setBlendedScore(blended);
        email.setBatchRank(1);
        return email;
    }
}
