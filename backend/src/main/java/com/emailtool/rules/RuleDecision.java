package com.emailtool.rules;

import com.emailtool.domain.Category;

public record RuleDecision(Category category, double confidence, String summary, boolean actionNeeded) {

    public RuleDecision(Category category, double confidence, String summary) {
        this(category, confidence, summary, category != null && category.isAction());
    }
}
