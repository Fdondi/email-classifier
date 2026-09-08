package com.emailtool.api;

import com.emailtool.api.dto.EmailSummaryDto;
import com.emailtool.config.AppProperties;
import com.emailtool.domain.Email;

public final class EmailMapper {

    private EmailMapper() {}

    public static EmailSummaryDto toSummary(Email email, AppProperties properties) {
        return new EmailSummaryDto(
                email.getId(),
                cap(email.getSubject(), properties.getSubjectMaxChars()),
                email.getFromAddress(),
                cap(email.getFromDisplayName(), properties.getSubjectMaxChars()),
                email.getDateSent(),
                email.getCategory(),
                email.getSummary(),
                email.getConfidence(),
                email.getInterestScore(),
                email.getDueDate(),
                email.getBlendedScore(),
                email.getBatchRank(),
                email.isInjectionSuspected(),
                email.isNeedsManualReview(),
                email.getClassificationSource() == null ? null : email.getClassificationSource().name());
    }

    public static String cap(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
