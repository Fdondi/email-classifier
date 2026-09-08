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
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DigestService {

    private static final int TOP_N = 5;

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

    public DigestDto digest() {
        List<EmailSummaryDto> quarantine = emailRepository.findAll().stream()
                .filter(email -> !FixtureMail.isFixture(email))
                .filter(Email::isInjectionSuspected)
                .sorted(Comparator.comparing(Email::getIngestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        List<Category> actionCats = List.of(
                Category.ACTION_NEEDED_PAY,
                Category.ACTION_NEEDED_SIGN,
                Category.ACTION_NEEDED_OTHER,
                Category.JOB_INTERVIEWS);
        List<EmailSummaryDto> action = emailRepository.findByCategoryIn(actionCats).stream()
                .filter(email -> !FixtureMail.isFixture(email))
                .filter(email -> !email.isInjectionSuspected())
                .sorted(Comparator.comparing(Email::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Email::getDateSent, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new DigestDto(
                quarantine,
                action,
                dual(Category.EVENT),
                dual(Category.JOB_OFFERS),
                summarized(Category.PROMOTION),
                summarized(Category.NEWSLETTER_TECH),
                summarized(Category.NEWSLETTER_OTHER),
                summarized(Category.OTHER),
                visible(emailRepository.findByCategory(Category.SPAM)).size(),
                triageJobService.lastError());
    }

    public CategoryPageDto page(Category category, int offset, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 5;
        }
        if (offset < 0) {
            offset = 0;
        }
        Page<Email> page = emailRepository.findByCategoryOrderByBatchRankAscIdAsc(
                category, PageRequest.of(0, Math.max(offset + limit, limit)));
        List<EmailSummaryDto> items = visible(page.getContent()).stream()
                .skip(offset)
                .limit(limit)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        long total = visible(emailRepository.findByCategory(category)).size();
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
                email.getMessageId());
    }

    private DualAxisSectionDto dual(Category category) {
        List<Email> emails = visible(emailRepository.findByCategoryAndInjectionSuspectedFalse(category));
        List<EmailSummaryDto> byUrgency = emails.stream()
                .sorted(Comparator.comparing(Email::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Email::getDateSent, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        List<EmailSummaryDto> byImportance = emails.stream()
                .sorted(Comparator.comparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Email::getBatchRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new DualAxisSectionDto(byUrgency, byImportance, emails.size());
    }

    private SummarizedSectionDto summarized(Category category) {
        List<Email> emails = visible(emailRepository.findByCategoryAndInjectionSuspectedFalse(category));
        List<EmailSummaryDto> top = emails.stream()
                .sorted(Comparator.comparing(Email::getBatchRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_N)
                .map(email -> EmailMapper.toSummary(email, properties))
                .toList();
        return new SummarizedSectionDto(top, emails.size());
    }

    private static List<Email> visible(List<Email> emails) {
        return emails.stream().filter(email -> !FixtureMail.isFixture(email)).toList();
    }
}
