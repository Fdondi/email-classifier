package com.emailtool.jobs;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code canonical_key}, cleaned URL and document id of letter-writer's {@code opportunities}
 * collection. A line-by-line port of letter-writer's {@code OpportunityKeys.kt} (same
 * {@code java.net.URI} semantics); both sides must agree, which {@code OpportunityKeysTest} checks
 * against letter-writer's shared fixture {@code opportunity_keys.json}.
 */
public final class OpportunityKeys {

    /** Query parameters dropped from stored URLs and {@code url:} keys (compared lowercase; {@code utm_*} by prefix). */
    public static final Set<String> TRACKING_PARAMS =
            Set.of("source", "uid", "mid", "trk", "refid", "trackingid", "gclid", "fbclid");

    private static final Pattern UUID_IN_PATH =
            Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern LINKEDIN_VIEW = Pattern.compile("/jobs/view/(?:[^/]*?-)?(\\d+)(?:/|$)");
    private static final Pattern SWISSDEVJOBS_PATH = Pattern.compile("^/jobs/([^/]+)");
    private static final Set<String> JOBCLOUD_HOSTS = Set.of("jobs.ch", "jobup.ch", "jobscout24.ch");

    private OpportunityKeys() {}

    public static String canonicalKey(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        URI uri = parse(url);
        if (uri == null) {
            return "url:" + url.toLowerCase(Locale.ROOT);
        }
        String host = host(uri);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        List<Map.Entry<String, String>> params = queryParams(uri);
        if (JOBCLOUD_HOSTS.stream().anyMatch(h -> onHost(host, h))) {
            Matcher m = UUID_IN_PATH.matcher(path.toLowerCase(Locale.ROOT));
            if (m.find()) {
                return "jobs.ch:" + m.group(1);
            }
        }
        if (onHost(host, "linkedin.com")) {
            Matcher m = LINKEDIN_VIEW.matcher(path);
            if (m.find()) {
                return "linkedin:" + m.group(1);
            }
            for (Map.Entry<String, String> p : params) {
                String v = p.getValue();
                if (p.getKey().equalsIgnoreCase("currentJobId")
                        && !v.isEmpty()
                        && v.chars().allMatch(Character::isDigit)) {
                    return "linkedin:" + v;
                }
            }
        }
        if (onHost(host, "swissdevjobs.ch")) {
            Matcher m = SWISSDEVJOBS_PATH.matcher(path);
            if (m.find()) {
                return "swissdevjobs:" + m.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return "url:" + normalizedUrl(uri, host).toLowerCase(Locale.ROOT);
    }

    /** The URL as stored on the opportunity: tracking parameters and fragment removed, case kept. */
    public static String cleanUrl(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        URI uri = parse(url);
        if (uri == null) {
            return url;
        }
        return normalizedUrl(uri, uri.getHost() == null ? "" : uri.getHost());
    }

    /**
     * The URL email-tool stores: {@link #cleanUrl}, except LinkedIn jobs become the bare
     * {@code /jobs/view/<id>/} page. Alert links carry one-time sign-in tokens
     * ({@code otpToken}, {@code midToken}) that must not end up in a shared database.
     */
    public static String storedUrl(String rawUrl) {
        String key = canonicalKey(rawUrl);
        if (key.startsWith("linkedin:")) {
            return "https://www.linkedin.com/jobs/view/" + key.substring("linkedin:".length()) + "/";
        }
        return cleanUrl(rawUrl);
    }

    /** {@code opportunities} document id: sha256 of {@code "<user_id>\n<canonical_key>"}, first 32 hex chars. */
    public static String documentId(String userId, String canonicalKey) {
        return sha256(userId + "\n" + canonicalKey).substring(0, 32);
    }

    /** {@code source} field for a key: jobs.ch, linkedin, swissdevjobs, or manual. */
    public static String sourceOf(String canonicalKey) {
        int colon = canonicalKey.indexOf(':');
        String prefix = colon < 0 ? canonicalKey : canonicalKey.substring(0, colon);
        return switch (prefix) {
            case "jobs.ch", "linkedin", "swissdevjobs" -> prefix;
            default -> "manual";
        };
    }

    static boolean onHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String normalizedUrl(URI uri, String host) {
        String scheme = (uri.getScheme() == null ? "https" : uri.getScheme()).toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (path.length() > 1) {
            int end = path.length();
            while (end > 0 && path.charAt(end - 1) == '/') {
                end--;
            }
            path = path.substring(0, end);
        }
        List<Map.Entry<String, String>> kept = new ArrayList<>();
        for (Map.Entry<String, String> p : queryParams(uri)) {
            if (!isTracking(p.getKey())) {
                kept.add(p);
            }
        }
        kept.sort(Comparator.<Map.Entry<String, String>, String>comparing(Map.Entry::getKey)
                .thenComparing(Map.Entry::getValue));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> p : kept) {
            query.append(query.isEmpty() ? '?' : '&').append(p.getKey());
            if (!p.getValue().isEmpty()) {
                query.append('=').append(p.getValue());
            }
        }
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        return scheme + "://" + host + port + path + query;
    }

    private static boolean isTracking(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("utm_") || TRACKING_PARAMS.contains(n);
    }

    private static URI parse(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isEmpty()) {
                return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private static String host(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** Raw (still percent-encoded) name/value pairs, so a cleaned URL stays valid. */
    private static List<Map.Entry<String, String>> queryParams(URI uri) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        String raw = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        for (String part : raw.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            out.add(eq < 0 ? Map.entry(part, "") : Map.entry(part.substring(0, eq), part.substring(eq + 1)));
        }
        return out;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
