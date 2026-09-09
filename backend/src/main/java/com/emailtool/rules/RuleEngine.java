package com.emailtool.rules;

import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RuleEngine {

    private final List<ClassificationRule> rules;

    public RuleEngine(List<ClassificationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public boolean classify(Email email) {
        for (ClassificationRule rule : rules) {
            Optional<RuleDecision> decision = rule.apply(email);
            if (decision.isPresent()) {
                RuleDecision value = decision.get();
                email.setCategory(value.category());
                email.setActionNeeded(value.actionNeeded());
                email.setConfidence(value.confidence());
                email.setSummary(value.summary());
                email.setInterestScore(50);
                email.setClassificationSource(ClassificationSource.RULE);
                email.setNeedsManualReview(false);
                email.setClassifiedAt(Instant.now());
                email.setOllamaError(null);
                return true;
            }
        }
        return false;
    }
}
