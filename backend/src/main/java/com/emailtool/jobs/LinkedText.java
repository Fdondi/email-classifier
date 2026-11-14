package com.emailtool.jobs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Mail body as text in which every web link is replaced by a marker such as {@code [L3]}. The model
 * answers with markers, never with URLs, so it cannot invent or alter a link, and long tracking URLs
 * do not eat the context window.
 */
public record LinkedText(String text, List<String> links) {

    static final int MAX_LINKS = 200;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"')\\]]+");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "br", "tr", "li", "ul", "ol", "table", "h1", "h2", "h3", "h4", "h5", "h6",
            "section", "article", "header", "footer", "hr", "blockquote");
    private static final Pattern BLANK_RUNS = Pattern.compile("\\n[ \\t\\u00a0]*(?:\\n[ \\t\\u00a0]*)+");
    private static final Pattern SPACE_RUNS = Pattern.compile("[ \\t\\u00a0]+");

    /** URL of marker {@code L<n>} (1-based), or null when the marker is unknown. */
    public String url(String marker) {
        if (marker == null) {
            return null;
        }
        String m = marker.trim().replace("[", "").replace("]", "").toUpperCase(Locale.ROOT);
        if (!m.startsWith("L")) {
            return null;
        }
        try {
            int n = Integer.parseInt(m.substring(1));
            return n >= 1 && n <= links.size() ? links.get(n - 1) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Prefer the text/plain part when it carries links (compact, keeps line structure); otherwise
     * walk the HTML, where links live in anchors.
     */
    public static LinkedText of(String plain, String html) {
        if (plain != null && URL.matcher(plain).find()) {
            return fromPlain(plain);
        }
        if (html != null && !html.isBlank()) {
            return fromHtml(html);
        }
        return fromPlain(plain == null ? "" : plain);
    }

    static LinkedText fromPlain(String plain) {
        Markers markers = new Markers();
        Matcher m = URL.matcher(plain);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String url = trimTrailingPunctuation(m.group());
            String rest = m.group().substring(url.length());
            // jobs.ch plain bodies leave "&amp;" escaped inside URLs.
            String marker = markers.markerFor(url.replace("&amp;", "&"));
            m.appendReplacement(out, Matcher.quoteReplacement((marker.isEmpty() ? "" : marker) + rest));
        }
        m.appendTail(out);
        return new LinkedText(tidy(out.toString()), markers.list());
    }

    static LinkedText fromHtml(String html) {
        Document document = Jsoup.parse(html);
        document.select("script, style, noscript, iframe, object, embed, head, [hidden], [aria-hidden=true]").remove();
        List<Element> hidden = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            String style = element.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
            if (style.contains("display:none") || style.contains("visibility:hidden")
                    || style.contains("font-size:0") || style.contains("opacity:0")) {
                hidden.add(element);
            }
        }
        hidden.forEach(Element::remove);
        Markers markers = new Markers();
        StringBuilder out = new StringBuilder();
        Element root = document.body() == null ? document : document.body();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode text) {
                    out.append(text.text());
                } else if (node instanceof Element el) {
                    String tag = el.normalName();
                    if (BLOCK_TAGS.contains(tag)) {
                        out.append('\n');
                    } else if (tag.equals("td") || tag.equals("th")) {
                        out.append(' ');
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el) {
                    String tag = el.normalName();
                    if (tag.equals("a")) {
                        String href = el.attr("href").trim();
                        if (href.startsWith("http://") || href.startsWith("https://")) {
                            String marker = markers.markerFor(href);
                            if (!marker.isEmpty()) {
                                out.append(' ').append(marker).append(' ');
                            }
                        }
                    } else if (BLOCK_TAGS.contains(tag)) {
                        out.append('\n');
                    }
                }
            }
        }, root);
        return new LinkedText(tidy(out.toString()), markers.list());
    }

    private static String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && ".,;:!?".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    private static String tidy(String text) {
        String spaced = SPACE_RUNS.matcher(text.replace("\r\n", "\n")).replaceAll(" ");
        return BLANK_RUNS.matcher(spaced).replaceAll("\n\n").strip();
    }

    private static final class Markers {
        private final Map<String, String> byUrl = new LinkedHashMap<>();

        String markerFor(String url) {
            String existing = byUrl.get(url);
            if (existing != null) {
                return existing;
            }
            if (byUrl.size() >= MAX_LINKS) {
                return "";
            }
            String marker = "[L" + (byUrl.size() + 1) + "]";
            byUrl.put(url, marker);
            return marker;
        }

        List<String> list() {
            return List.copyOf(byUrl.keySet());
        }
    }
}
