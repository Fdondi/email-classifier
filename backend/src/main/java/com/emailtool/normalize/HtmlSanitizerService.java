package com.emailtool.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizerService {

    static final String PLACEHOLDER_DATA_URI =
            "data:image/svg+xml;utf8,"
                    + "<svg xmlns='http://www.w3.org/2000/svg' width='48' height='32'>"
                    + "<rect width='100%25' height='100%25' fill='%23ddd'/>"
                    + "<text x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23666' font-size='8'>blocked</text>"
                    + "</svg>";

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "a",
                    "p",
                    "div",
                    "span",
                    "br",
                    "ul",
                    "ol",
                    "li",
                    "strong",
                    "em",
                    "b",
                    "i",
                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "blockquote",
                    "pre",
                    "code",
                    "table",
                    "thead",
                    "tbody",
                    "tr",
                    "td",
                    "th",
                    "img")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href")
            .onElements("a")
            .allowAttributes("src", "alt")
            .onElements("img")
            .toFactory();

    public SanitizeResult sanitize(String html) {
        if (html == null || html.isBlank()) {
            return new SanitizeResult("", List.of());
        }
        String afterOwasp = POLICY.sanitize(html);
        Document document = Jsoup.parseBodyFragment(afterOwasp);
        document.outputSettings().prettyPrint(false);
        List<String> remoteUrls = new ArrayList<>();
        int index = 0;
        for (Element img : document.select("img")) {
            String src = img.attr("src");
            img.removeAttr("style");
            if (src == null || src.isBlank()) {
                img.remove();
                continue;
            }
            String trimmed = src.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
                img.remove();
                continue;
            }
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                remoteUrls.add(trimmed);
                img.attr("src", PLACEHOLDER_DATA_URI);
                img.attr("alt", img.hasAttr("alt") ? img.attr("alt") : "remote image blocked");
                img.attr("data-remote-index", Integer.toString(index));
                index++;
            } else {
                img.remove();
            }
        }
        for (Element styled : document.select("[style]")) {
            styled.removeAttr("style");
        }
        document.select("style, script, iframe, object, embed, form, input, base, link, meta").remove();
        Element body = document.body();
        String sanitized = body == null ? "" : body.html();
        return new SanitizeResult(sanitized, List.copyOf(remoteUrls));
    }

    public String escapePlainToHtml(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        return Jsoup.clean(plain, Safelist.none());
    }

    public record SanitizeResult(String html, List<String> remoteImageUrls) {}
}
