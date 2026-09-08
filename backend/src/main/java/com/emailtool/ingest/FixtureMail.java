package com.emailtool.ingest;

import com.emailtool.domain.Email;
import java.util.Locale;

public final class FixtureMail {

    private FixtureMail() {}

    public static boolean isFixture(Email email) {
        if (email == null) {
            return false;
        }
        return isFixturePath(email.getSourcePath()) || isFixtureSubject(email.getSubject());
    }

    public static boolean isFixturePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/testdata/");
    }

    public static boolean isFixtureSubject(String subject) {
        return subject != null && subject.startsWith("FAKE TEST DATA");
    }
}
