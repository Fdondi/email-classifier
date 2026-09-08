package com.emailtool.rules;

import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class AuthFailureRule implements ClassificationRule {

    private final AddressLists addressLists;

    public AuthFailureRule(AddressLists addressLists) {
        this.addressLists = addressLists;
    }

    @Override
    public Optional<RuleDecision> apply(Email email) {
        if (email.getAuthenticationResults() == null || email.getAuthenticationResults().isBlank()) {
            return Optional.empty();
        }
        if (addressLists.isContact(email.getFromAddress())) {
            return Optional.empty();
        }
        if (isFail(email.getDkimResult()) && isFail(email.getSpfResult()) && isFail(email.getDmarcResult())) {
            return Optional.of(new RuleDecision(Category.SPAM, 0.85, "SPF, DKIM, and DMARC all failed"));
        }
        return Optional.empty();
    }

    private static boolean isFail(String result) {
        return result != null && "fail".equals(result.toLowerCase(Locale.ROOT));
    }
}
