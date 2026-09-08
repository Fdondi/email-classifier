package com.emailtool.ingest;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthResultsParser {

    private static final Pattern RESULT = Pattern.compile("(dkim|spf|dmarc)\\s*=\\s*([a-z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private AuthResultsParser() {}

    public static AuthResults parse(String header) {
        AuthResults results = new AuthResults();
        if (header == null || header.isBlank()) {
            return results;
        }
        Matcher matcher = RESULT.matcher(header);
        while (matcher.find()) {
            String method = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2).toLowerCase(Locale.ROOT);
            switch (method) {
                case "dkim" -> results.dkim = value;
                case "spf" -> results.spf = value;
                case "dmarc" -> results.dmarc = value;
                default -> {
                    throw new IllegalStateException("Unexpected auth method: " + method);
                }
            }
        }
        return results;
    }

    public static final class AuthResults {
        public String dkim;
        public String spf;
        public String dmarc;
    }
}
