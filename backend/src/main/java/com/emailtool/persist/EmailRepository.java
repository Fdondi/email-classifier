package com.emailtool.persist;

import com.emailtool.domain.Category;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailRepository extends JpaRepository<Email, Long> {

    /** Sender, subject and category only: loading Email entities would pull every raw MIME body too. */
    interface MailHead {
        Long getId();

        Long getGmailMsgId();

        String getFromAddress();

        String getSubject();

        Instant getDateSent();

        Category getCategory();
    }

    List<MailHead> findByDateSentAfter(Instant since);

    Optional<Email> findByMessageId(String messageId);

    Optional<Email> findBySourcePath(String sourcePath);

    List<Email> findBySourcePathStartingWith(String sourcePathPrefix);

    List<Email> findByCategoryIsNull();

    long countByCategoryIsNull();

    List<Email> findByInjectionSuspectedTrueAndCategoryNot(Category spam);

    List<Email> findByCategoryIn(Collection<Category> categories);

    List<Email> findByActionNeededTrue();

    List<Email> findByCategory(Category category);

    Page<Email> findByCategoryOrderByBatchRankAscIdAsc(Category category, Pageable pageable);

    long countByCategory(Category category);

    List<Email> findByCategoryAndInjectionSuspectedFalse(Category category);

    List<Email> findByClassificationSource(ClassificationSource source);

    List<Email> findByClassificationSourceAndSummary(ClassificationSource source, String summary);

    /**
     * The unread digest's action needed / events / job offers totals, counted in SQL. Loading the
     * entities would pull every raw MIME body (~2 GB of garbage) just for three numbers. Must match
     * DigestService's filters: shown (visible, not hidden, not a fixture, unread), not quarantined.
     */
    @Query(nativeQuery = true, value = """
            select
              coalesce(sum(action_needed = 1 and (category is null or category <> 'SPAM')), 0),
              coalesce(sum(category = 'EVENT'), 0),
              coalesce(sum(category = 'JOB_OFFERS'), 0)
            from email
            where digest_visible = 1 and user_hidden = 0 and unread = 1 and injection_suspected = 0
              and lower(replace(source_path, char(92), '/')) not like '%/testdata/%'
              and (subject is null or subject not glob 'FAKE TEST DATA*')
            """)
    List<Object[]> unreadDigestTotals();
}
