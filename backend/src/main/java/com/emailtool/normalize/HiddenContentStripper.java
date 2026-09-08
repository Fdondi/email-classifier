package com.emailtool.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class HiddenContentStripper {

    public String toVisibleText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parse(html);
        document.select("script, style, noscript, iframe, object, embed, [hidden], [aria-hidden=true]").remove();
        List<Element> hidden = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            if (isHidden(element)) {
                hidden.add(element);
            }
        }
        for (Element element : hidden) {
            element.remove();
        }
        return document.body() == null ? "" : document.body().text();
    }

    private static boolean isHidden(Element element) {
        String style = element.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        if (style.contains("display:none")
                || style.contains("visibility:hidden")
                || style.contains("font-size:0")
                || style.contains("opacity:0")) {
            return true;
        }
        String hidden = element.attr("hidden");
        if (hidden != null && !hidden.isEmpty()) {
            return true;
        }
        String aria = element.attr("aria-hidden");
        return "true".equalsIgnoreCase(aria);
    }

    public boolean looksLikeColorHide(String html) {
        if (html == null) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("color:#fff") && lower.contains("background") && lower.contains("#fff")
                || lower.contains("color: white") && lower.contains("background");
    }
}
