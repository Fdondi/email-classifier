package com.emailtool.api;

import com.emailtool.api.dto.CategoryPageDto;
import com.emailtool.api.dto.DigestDto;
import com.emailtool.api.dto.DualAxisSectionDto;
import com.emailtool.api.dto.EmailDetailDto;
import com.emailtool.api.dto.EmailSummaryDto;
import com.emailtool.api.dto.SummarizedSectionDto;
import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.ingest.FixtureMail;
import com.emailtool.job.TriageJobService;
import com.emailtool.persist.EmailRemoteResourceRepository;
import com.emailtool.persist.EmailRepository;
import com.emailtool.rank.RatingScores;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DigestService {

    private static final int TOP_N = 5;

    static final Comparator<Email> BY_LIST_RANK =
            Comparator.comparing(Email::getListRank, Comparator.nullsLast(Comparator.naturalOrder()));
    static final Comparator<Email> BY_DUE_THEN_SENT = expiredFirst(
            BY_LIST_RANK
                    .thenComparing(Email::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Email::getDateSent, Comparator.nullsLast(Comparator.naturalOrder())));
    static final Comparator<Email> BY_IMPORTANCE = expiredFirst(
            Comparator.comparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Email::getBatchRank, Comparator.nullsLast(Comparator.naturalOrder())));
    static final Comparator<Email> BY_SUMMARIZED = expiredFirst(
            BY_LIST_RANK
                    .thenComparing(Email::getBatchRank, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder())));
    static final Comparator<Email> BY_INGESTED = expiredFirst(
            Comparator.comparing(Email::getIngestedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    static final Comparator<Email> BY_DUAL_URGENCY = Comparator
            .comparingDouble((Email email) -> RatingScores.urgency(email, LocalDate.now()))
            .reversed()
            .thenComparing(BY_DUE_THEN_SENT);
    static final Comparator<Email> BY_DUAL_IMPORTANCE = Comparator
            .comparingDouble(RatingScores::importance)
            .reversed()
            .thenComparing(Email::getBatchRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Email::getId);

    private final EmailRepository emailRepository;
    private final EmailRemoteResourceRepository remoteResourceRepository;
    private final AppProperties properties;
    private final TriageJobService triageJobService;

    public DigestService(
            EmailRepository emailRepository,
            EmailRemoteResourceRepository remoteResourceRepository,
            AppProperties properties,
            TriageJobService triageJobService) {
        this.emailRepository = emailRepository;
        this.remoteResourceRepository = remoteResourceRepository;
        this.properties = properties;
        this.triageJobService = triageJobService;
    }

    public DigestDto digest(boolean includeRead) {
        Predicate<Email> shown = shownFilter(includeRead);
        List<EmailSummaryDto> quarantine = emailRepository.findAll().stream()
                .filter(shown)
                .filter(Email::isInjectionSuspected)
                .sorted(BY_INGESTED)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new DigestDto(
                quarantine,
                dualSection(visible(emailRepository.findByActionNeededTrue(), shown).stream()
                        .filter(DigestService::inActionNeededDigest)
                        .toList()),
                dual(Category.EVENT, shown),
                dual(Category.JOB_OFFERS, shown),
                summarized(Category.PROMOTION, shown),
                summarized(Category.NEWSLETTER_TECH, shown),
                summarized(Category.NEWSLETTER_OTHER, shown),
                summarized(Category.OTHER, shown),
                visible(emailRepository.findByCategory(Category.SPAM), shown).size(),
                emailRepository.findAll().stream()
                        .filter(Email::isUserHidden)
                        .filter(Email::isDigestVisible)
                        .filter(email -> !FixtureMail.isFixture(email))
                        .count(),
                triageJobService.lastError());
    }

    public List<EmailSummaryDto> hidden(int offset, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 50;
        }
        if (offset < 0) {
            offset = 0;
        }
        return emailRepository.findAll().stream()
                .filter(Email::isUserHidden)
                .filter(Email::isDigestVisible)
                .filter(email -> !FixtureMail.isFixture(email))
                .sorted(Comparator.comparing(Email::getDateSent, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(offset)
                .limit(limit)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
    }

    public void setUserHidden(long id, boolean hidden) {
        Email email = emailRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        email.setUserHidden(hidden);
        emailRepository.save(email);
    }

    public void setListRanks(List<Long> ids) {
        int rank = 0;
        for (Long id : ids) {
            Email email = emailRepository
                    .findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
            email.setListRank(rank++);
            emailRepository.save(email);
        }
    }

    public CategoryPageDto page(Category category, int offset, int limit, boolean includeRead) {
        if (limit <= 0 || limit > 50) {
            limit = 5;
        }
        if (offset < 0) {
            offset = 0;
        }
        Predicate<Email> shown = shownFilter(includeRead);
        List<Email> emails = category == Category.OTHER
                ? visible(otherDigestEmails(), shown)
                : visible(emailRepository.findByCategory(category), shown);
        List<EmailSummaryDto> items = emails.stream()
                .sorted(BY_SUMMARIZED)
                .skip(offset)
                .limit(limit)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        long total = emails.size();
        return new CategoryPageDto(category, total, offset, limit, items);
    }

    public EmailDetailDto detail(long id) {
        Email email = emailRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        int remoteCount = remoteResourceRepository.findByEmailIdOrderByResourceIndexAsc(id).size();
        return new EmailDetailDto(
                email.getId(),
                EmailMapper.cap(email.getSubject(), properties.getSubjectMaxChars()),
                email.getFromAddress(),
                EmailMapper.cap(email.getFromDisplayName(), properties.getSubjectMaxChars()),
                email.getDateSent(),
                email.getCategory(),
                email.isActionNeeded(),
                email.getSummary(),
                email.getConfidence(),
                email.getInterestScore(),
                email.getDueDate(),
                email.isInjectionSuspected(),
                email.isNeedsManualReview(),
                email.isImagesLoaded(),
                remoteCount,
                email.getClassificationSource() == null ? null : email.getClassificationSource().name(),
                email.getLanguage(),
                email.getMessageId(),
                email.isUserHidden(),
                GmailWebLinks.url(email.getGmailMsgId(), email.getMessageId()));
    }

    private DualAxisSectionDto dual(Category category, Predicate<Email> shown) {
        return dualSection(visible(emailRepository.findByCategoryAndInjectionSuspectedFalse(category), shown));
    }

    private DualAxisSectionDto dualSection(List<Email> emails) {
        List<EmailSummaryDto> byUrgency = emails.stream()
                .sorted(BY_DUAL_URGENCY)
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        List<EmailSummaryDto> byImportance = emails.stream()
                .sorted(BY_DUAL_IMPORTANCE)
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        List<EmailSummaryDto> allEmails = emails.stream()
                .sorted(BY_DUE_THEN_SENT)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new DualAxisSectionDto(byUrgency, byImportance, allEmails, emails.size());
    }

    private SummarizedSectionDto summarized(Category category, Predicate<Email> shown) {
        List<Email> emails = category == Category.OTHER
                ? visible(otherDigestEmails(), shown)
                : visible(emailRepository.findByCategoryAndInjectionSuspectedFalse(category), shown);
        List<EmailSummaryDto> top = emails.stream()
                .sorted(BY_SUMMARIZED)
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new SummarizedSectionDto(top, emails.size());
    }

    private List<Email> otherDigestEmails() {
        return emailRepository.findAll().stream().filter(DigestService::inOtherDigest).toList();
    }

    static boolean inActionNeededDigest(Email email) {
        return email.isActionNeeded()
                && !email.isInjectionSuspected()
                && email.getCategory() != Category.SPAM;
    }

    static boolean inOtherDigest(Email email) {
        Category category = email.getCategory();
        if (category == Category.OTHER) {
            return !email.isInjectionSuspected();
        }
        return category != null && category.isAction() && !email.isActionNeeded() && !email.isInjectionSuspected();
    }

    private static Predicate<Email> shownFilter(boolean includeRead) {
        return email -> email.isDigestVisible()
                && !email.isUserHidden()
                && !FixtureMail.isFixture(email)
                && (includeRead || email.isUnread());
    }

    private static List<Email> visible(List<Email> emails, Predicate<Email> shown) {
        return emails.stream().filter(shown).toList();
    }

    static Comparator<Email> expiredFirst(Comparator<Email> then) {
        return Comparator.<Email, Boolean>comparing(DigestService::expired).reversed().thenComparing(then);
    }

    static boolean expired(Email email) {
        LocalDate due = email.getDueDate();
        return due != null && due.isBefore(LocalDate.now());
    }
}
