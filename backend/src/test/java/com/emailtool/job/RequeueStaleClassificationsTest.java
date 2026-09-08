package com.emailtool.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import com.emailtool.feedback.CorrectionService;
import org.junit.jupiter.api.Test;

class RequeueStaleClassificationsTest {

    @Test
    void keepsUserAndLlmLabels() {
        Email user = new Email();
        user.setClassificationSource(ClassificationSource.USER);
        user.setSummary(CorrectionService.LIST_UNSUBSCRIBE_SUMMARY);
        assertFalse(RequeueStaleClassifications.shouldRequeue(user));

        Email llm = new Email();
        llm.setClassificationSource(ClassificationSource.LLM);
        assertFalse(RequeueStaleClassifications.shouldRequeue(llm));
    }

    @Test
    void requeuesUnsubscribeRuleGuessAndFallback() {
        Email rule = new Email();
        rule.setClassificationSource(ClassificationSource.RULE);
        rule.setSummary(CorrectionService.LIST_UNSUBSCRIBE_SUMMARY);
        assertTrue(RequeueStaleClassifications.shouldRequeue(rule));

        Email billing = new Email();
        billing.setClassificationSource(ClassificationSource.RULE);
        billing.setSummary("Known billing sender");
        assertFalse(RequeueStaleClassifications.shouldRequeue(billing));

        Email fallback = new Email();
        fallback.setClassificationSource(ClassificationSource.FALLBACK);
        assertTrue(RequeueStaleClassifications.shouldRequeue(fallback));
    }

    @Test
    void doesNotRequeueUnclassified() {
        assertFalse(RequeueStaleClassifications.shouldRequeue(new Email()));
    }
}
