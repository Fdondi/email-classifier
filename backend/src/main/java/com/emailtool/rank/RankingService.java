package com.emailtool.rank;

import com.emailtool.classify.LlmClassifier;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.domain.SenderPrior;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.SenderPriorRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    /** Gemma 4 crashes the predict stream on a large unconstrained rank prompt. */
    static final int MAX_LLM_RANK = 20;

    private final EmailRepository emailRepository;
    private final SenderPriorRepository senderPriorRepository;
    private final LlmClassifier classifier;

    public RankingService(
            EmailRepository emailRepository, SenderPriorRepository senderPriorRepository, LlmClassifier classifier) {
        this.emailRepository = emailRepository;
        this.senderPriorRepository = senderPriorRepository;
        this.classifier = classifier;
    }

    public void rankAll() {
        for (Category category : Category.values()) {
            List<Email> emails = emailRepository.findByCategory(category);
            for (Email email : emails) {
                if (email.getRatingImportance() != null) {
                    email.setBlendedScore(email.getRatingImportance());
                } else {
                    email.setBlendedScore(blend(email));
                }
            }
            if (category.isSummarized() || category.isDualAxis()) {
                applyBatchRank(emails);
            } else if (category.isAction()) {
                emails.sort(Comparator.comparing(Email::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Email::getDateSent, Comparator.nullsLast(Comparator.naturalOrder())));
                assignOrder(emails);
            } else {
                emails.sort(Comparator.comparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Email::getId));
                assignOrder(emails);
            }
            emailRepository.saveAll(emails);
        }
    }

    private void applyBatchRank(List<Email> emails) {
        emails.sort(Comparator.comparing(Email::getBlendedScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Email::getId));
        if (!shouldLlmRank(emails.size())) {
            if (emails.size() > MAX_LLM_RANK) {
                log.info(
                        "Skipping LLM rank of {} emails (cap {}); using blended scores so the GPU can be freed",
                        emails.size(),
                        MAX_LLM_RANK);
            }
            assignOrder(emails);
            return;
        }
        List<LlmClassifier.RankItem> items = new ArrayList<>();
        for (Email email : emails) {
            items.add(new LlmClassifier.RankItem(email.getId(), email.getFromAddress(), email.getSummary()));
        }
        Optional<List<Long>> ranked = classifier.rank(items);
        if (ranked.isPresent()) {
            List<Long> order = ranked.get();
            emails.sort(Comparator.comparingInt(email -> order.indexOf(email.getId())));
        }
        assignOrder(emails);
    }

    static boolean shouldLlmRank(int size) {
        return size >= 2 && size <= MAX_LLM_RANK;
    }

    private void assignOrder(List<Email> emails) {
        int rank = 1;
        for (Email email : emails) {
            email.setBatchRank(rank++);
        }
    }

    private double blend(Email email) {
        double model = email.getInterestScore() == null ? 0.5 : email.getInterestScore() / 100.0;
        double prior = 0.5;
        if (email.getFromAddress() != null) {
            prior = senderPriorRepository
                    .findById(email.getFromAddress().toLowerCase())
                    .map(SenderPrior::blendedPrior)
                    .orElse(0.5);
        }
        return 0.5 * model + 0.5 * prior;
    }
}
