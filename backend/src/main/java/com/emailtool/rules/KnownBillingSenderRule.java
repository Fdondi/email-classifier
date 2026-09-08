package com.emailtool.rules;

import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class KnownBillingSenderRule implements ClassificationRule {

    private final AddressLists addressLists;

    public KnownBillingSenderRule(AddressLists addressLists) {
        this.addressLists = addressLists;
    }

    @Override
    public Optional<RuleDecision> apply(Email email) {
        if (!addressLists.isBillingSender(email.getFromAddress())) {
            return Optional.empty();
        }
        return Optional.of(new RuleDecision(
                Category.ACTION_NEEDED_PAY, 0.9, "Known billing sender"));
    }
}
