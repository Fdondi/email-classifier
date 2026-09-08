package com.emailtool.classify;

import com.emailtool.domain.Category;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClassifierJson(
        Category category,
        Double confidence,
        String summary,
        @JsonAlias({"interest_score"}) Integer interestScore,
        @JsonAlias({"due_date"}) LocalDate dueDate,
        @JsonAlias({"injection_suspected"}) Boolean injectionSuspected,
        String language) {}
