package com.emailtool.rules;

import com.emailtool.domain.Email;
import java.util.Optional;

public interface ClassificationRule {

    Optional<RuleDecision> apply(Email email);
}
