package com.emailtool.tray;

import static org.assertj.core.api.Assertions.assertThat;

import com.emailtool.api.DigestService;
import com.emailtool.api.dto.DigestDto;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.persist.EmailRepository;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The tray's SQL totals must stay equal to what the digest shows, filter for filter. */
@SpringBootTest
class UnreadDigestTotalsTest {

    private static final String PREFIX = "test-tray/";

    @Autowired EmailRepository emailRepository;
    @Autowired DigestService digestService;

    @AfterEach
    void clean() {
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith(PREFIX));
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith("C:\\repo\\testdata\\"));
    }

    @Test
    void sqlTotalsMatchTheDigestAcrossEveryFilter() {
        long[] before = sql();

        save(Category.OTHER, e -> e.setActionNeeded(true));
        save(Category.SPAM, e -> e.setActionNeeded(true));
        save(Category.EVENT, e -> {});
        save(Category.EVENT, e -> e.setSubject("fake test data, lower case: not a fixture"));
        save(Category.JOB_OFFERS, e -> {});
        save(Category.EVENT, e -> e.setUnread(false));
        save(Category.EVENT, e -> e.setUserHidden(true));
        save(Category.EVENT, e -> e.setDigestVisible(false));
        save(Category.EVENT, e -> e.setInjectionSuspected(true));
        save(Category.EVENT, e -> e.setSubject("FAKE TEST DATA invite"));
        save(Category.JOB_OFFERS, e -> e.setSourcePath("C:\\repo\\testdata\\eml\\" + System.nanoTime() + ".eml"));

        long[] after = sql();
        assertThat(new long[] {after[0] - before[0], after[1] - before[1], after[2] - before[2]})
                .containsExactly(1, 2, 1);
        DigestDto digest = digestService.digest(false, null);
        assertThat(after).containsExactly(
                digest.actionNeeded().total(), digest.events().total(), digest.jobOffers().total());
    }

    private long[] sql() {
        Object[] row = emailRepository.unreadDigestTotals().get(0);
        return new long[] {((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()};
    }

    private void save(Category category, Consumer<Email> tweak) {
        Email email = new Email();
        email.setSourcePath(PREFIX + System.nanoTime());
        email.setIngestedAt(Instant.parse("2026-11-10T08:00:00Z"));
        email.setDateSent(Instant.parse("2026-11-10T08:00:00Z"));
        email.setSubject("Hello");
        email.setCategory(category);
        tweak.accept(email);
        emailRepository.save(email);
    }
}
