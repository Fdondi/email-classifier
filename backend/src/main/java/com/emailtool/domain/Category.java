package com.emailtool.domain;

public enum Category {
    ACTION_NEEDED_PAY,
    ACTION_NEEDED_SIGN,
    ACTION_NEEDED_OTHER,
    JOB_INTERVIEWS,
    EVENT,
    JOB_OFFERS,
    PROMOTION,
    NEWSLETTER_TECH,
    NEWSLETTER_OTHER,
    OTHER,
    SPAM;

    public static final int TAXONOMY_VERSION = 1;

    public boolean isAction() {
        return switch (this) {
            case ACTION_NEEDED_PAY, ACTION_NEEDED_SIGN, ACTION_NEEDED_OTHER, JOB_INTERVIEWS -> true;
            case EVENT, JOB_OFFERS, PROMOTION, NEWSLETTER_TECH, NEWSLETTER_OTHER, OTHER, SPAM -> false;
        };
    }

    public boolean isDualAxis() {
        return switch (this) {
            case EVENT, JOB_OFFERS -> true;
            case ACTION_NEEDED_PAY, ACTION_NEEDED_SIGN, ACTION_NEEDED_OTHER, JOB_INTERVIEWS,
                    PROMOTION, NEWSLETTER_TECH, NEWSLETTER_OTHER, OTHER, SPAM -> false;
        };
    }

    public boolean isSummarized() {
        return switch (this) {
            case PROMOTION, NEWSLETTER_TECH, NEWSLETTER_OTHER, OTHER -> true;
            case ACTION_NEEDED_PAY, ACTION_NEEDED_SIGN, ACTION_NEEDED_OTHER, JOB_INTERVIEWS,
                    EVENT, JOB_OFFERS, SPAM -> false;
        };
    }
}
