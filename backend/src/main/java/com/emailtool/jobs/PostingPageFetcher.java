package com.emailtool.jobs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads one job posting page. Most sites: plain HTTP, only paths robots.txt allows, redirects
 * followed by hand so every hop gets the same checks. Job boards ({@code browserDomains}: their
 * robots.txt shuts out every bot, and some serve only to browsers) are opened once in a headless
 * browser as a signed-out visitor, at the cleaned URL so alert sign-in tokens never reach the site.
 * A JobCloud page view still counts as engagement on the posting. Pages built by scripts also go
 * through the browser.
 */
public class PostingPageFetcher {

    static final int MAX_REDIRECTS = 5;
    static final int MAX_PAGE_BYTES = 3_000_000;
    /** letter-writer keeps capture text and HTML within these (larger HTML is dropped there too). */
    static final int MAX_TEXT_CHARS = 100_000;
    static final int MAX_HTML_CHARS = 300_000;
    /** Less visible text than this is a script-rendered shell, not a posting. */
    static final int MIN_TEXT_CHARS = 300;

    public record Page(String finalUrl, String text, String html) {}

    /** Loads a page in a browser and returns its DOM as HTML. */
    @FunctionalInterface
    public interface Renderer {
        String render(String url) throws IOException, InterruptedException;
    }

    /** Titles of login walls and bot checks, which are no posting. */
    private static final Pattern WALL_TITLE = Pattern.compile(
            "(?i)\\b(sign[ -]?in|log[ -]?in|sign[ -]?up|captcha|security check|access denied|just a moment|attention required)\\b");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    /** Either a page, or why only the link can be sent. */
    public record Result(Page page, String linkOnlyReason) {
        static Result linkOnly(String reason) {
            return new Result(null, reason);
        }
    }

    private final HttpClient http;
    private final List<String> browserDomains;
    private final String userAgent;
    private final Function<String, String> visibleText;
    private final Renderer browser;
    private final Map<String, RobotsTxt> robotsByOrigin = new HashMap<>();

    /** {@code browser} null: browser-only and script-built pages are sent as a link only. */
    public PostingPageFetcher(
            List<String> browserDomains, String userAgent, Function<String, String> visibleText, Renderer browser) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), browserDomains, userAgent, visibleText, browser);
    }

    PostingPageFetcher(HttpClient http, List<String> browserDomains, String userAgent,
            Function<String, String> visibleText, Renderer browser) {
        this.http = http;
        this.browserDomains = browserDomains.stream().map(d -> d.toLowerCase(Locale.ROOT).trim()).toList();
        this.userAgent = userAgent;
        this.visibleText = visibleText;
        this.browser = browser;
    }

    boolean browserOnly(String host) {
        return browserDomains.stream().anyMatch(d -> OpportunityKeys.onHost(host, d));
    }

    public Result fetch(String url) {
        String current = url;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                URI uri = URI.create(current);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                // Mail links are untrusted: a file:// or other scheme must never reach the browser.
                if (uri.getHost() == null || !(scheme.equals("http") || scheme.equals("https"))) {
                    return Result.linkOnly("not a web address");
                }
                if (browserOnly(uri.getHost().toLowerCase(Locale.ROOT))) {
                    return rendered(OpportunityKeys.storedUrl(current));
                }
                if (!robots(uri).allows(pathAndQuery(uri))) {
                    return Result.linkOnly("robots.txt disallows " + uri.getHost());
                }
                HttpResponse<byte[]> response = http.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse(null);
                    if (location == null) {
                        return Result.linkOnly("redirect without a target");
                    }
                    current = uri.resolve(location.trim()).toString();
                    continue;
                }
                if (status != 200) {
                    return Result.linkOnly("page answered HTTP " + status);
                }
                String type = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
                if (!type.contains("html")) {
                    return Result.linkOnly("not an HTML page (" + type + ")");
                }
                byte[] bytes = response.body();
                if (bytes.length > MAX_PAGE_BYTES) {
                    return Result.linkOnly("page too large");
                }
                String html = new String(bytes, charset(type));
                if (visibleText.apply(html).strip().length() < MIN_TEXT_CHARS) {
                    return rendered(current); // built by scripts: let a browser run them
                }
                return page(current, html);
            }
            return Result.linkOnly("too many redirects");
        } catch (IOException | IllegalArgumentException e) {
            return Result.linkOnly("download failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.linkOnly("download interrupted");
        }
    }

    private Result rendered(String url) throws IOException, InterruptedException {
        if (browser == null) {
            return Result.linkOnly("page needs a browser and none is configured");
        }
        return page(url, browser.render(url));
    }

    private Result page(String url, String html) {
        String text = visibleText.apply(html).strip();
        if (text.length() < MIN_TEXT_CHARS) {
            return Result.linkOnly("page has no readable text");
        }
        Matcher title = TITLE.matcher(html);
        if (title.find() && WALL_TITLE.matcher(title.group(1)).find()) {
            return Result.linkOnly("page asks to sign in or prove it is not a bot");
        }
        return new Result(new Page(
                url,
                text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text,
                html.length() <= MAX_HTML_CHARS ? html : null), null);
    }

    private RobotsTxt robots(URI uri) throws IOException, InterruptedException {
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        RobotsTxt cached = robotsByOrigin.get(origin);
        if (cached != null) {
            return cached;
        }
        RobotsTxt robots;
        try {
            HttpResponse<String> response = http.send(
                    request(URI.create(origin + "/robots.txt")), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                robots = RobotsTxt.parse(response.body(), "email-tool");
            } else if (status >= 400 && status < 500) {
                robots = RobotsTxt.ALLOW_ALL; // RFC 9309: no robots.txt, crawling allowed
            } else {
                robots = RobotsTxt.DISALLOW_ALL; // redirects, 5xx: treat as unavailable
            }
        } catch (IOException e) {
            robots = RobotsTxt.DISALLOW_ALL;
        }
        robotsByOrigin.put(origin, robots);
        return robots;
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .GET()
                .build();
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static java.nio.charset.Charset charset(String contentType) {
        int at = contentType.indexOf("charset=");
        if (at >= 0) {
            String name = contentType.substring(at + 8).replace("\"", "").split("[;\\s]")[0];
            try {
                return java.nio.charset.Charset.forName(name);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }
}
