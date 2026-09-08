package com.emailtool.rules;

import com.emailtool.domain.Category;

public record RuleDecision(Category category, double confidence, String summary) {}
