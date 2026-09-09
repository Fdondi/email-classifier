package com.emailtool.job;

import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import com.emailtool.feedback.CorrectionService;
import com.emailtool.persist.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequeueStaleClassifications {

    private static final Logger log = LoggerFactory.getLogger(RequeueStaleClassifications.class);

    private final EmailRepository emailRepository;

    public RequeueStaleClassifications(EmailRepository emailRepository) {
        this.emailRepository = emailRepository;
    }

    @Transactional
    public int requeue() {
        int n = 0;
        for (Email email : emailRepository.findByClassificationSourceAndSummary(
                ClassificationSource.RULE, CorrectionService.LIST_UNSUBSCRIBE_SUMMARY)) {
            n += clearForLlm(email);
        }
        for (Email email : emailRepository.findByClassificationSource(ClassificationSource.FALLBACK)) {
            n += clearForLlm(email);
        }
        if (n > 0) {
            log.info("Requeued {} emails for LLM (list-unsubscribe rule guesses and FALLBACK). USER labels kept.", n);
        }
        return n;
    }

    private int clearForLlm(Email email) {
        if (!shouldRequeue(email)) {
            return 0;
        }
        email.setCategory(null);
        email.setActionNeeded(false);
        email.setClassificationSource(null);
        email.setNeedsManualReview(false);
        email.setOllamaError(null);
        email.setConfidence(null);
        if (CorrectionService.LIST_UNSUBSCRIBE_SUMMARY.equals(email.getSummary())) {
            email.setSummary(null);
        }
        emailRepository.save(email);
        return 1;
    }

    static boolean shouldRequeue(Email email) {
        ClassificationSource source = email.getClassificationSource();
        if (source == null) {
            return false;
        }
        switch (source) {
            case USER:
            case LLM:
                return false;
            case FALLBACK:
                return true;
            case RULE:
                return CorrectionService.LIST_UNSUBSCRIBE_SUMMARY.equals(email.getSummary());
            default: {
                ClassificationSource exhaustive = source;
                throw new IllegalStateException("Unhandled source: " + exhaustive);
            }
        }
    }
}
