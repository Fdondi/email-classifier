package com.emailtool.normalize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class HtmlSanitizerServiceTest {

    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService();

    @Test
    void xssCorpusIsStripped() throws Exception {
        String html = new String(
                getClass().getResourceAsStream("/xss/payload.html").readAllBytes(), StandardCharsets.UTF_8);
        HtmlSanitizerService.SanitizeResult result = sanitizer.sanitize(html);
        String out = result.html().toLowerCase(Locale.ROOT);
        assertFalse(out.contains("<script"));
        assertFalse(out.contains("<iframe"));
        assertFalse(out.contains("<object"));
        assertFalse(out.contains("<embed"));
        assertFalse(out.contains("<form"));
        assertFalse(out.contains("<input"));
        assertFalse(out.contains("<base"));
        assertFalse(out.contains("<link"));
        assertFalse(out.contains("<meta"));
        assertFalse(out.contains("<style"));
        assertFalse(out.contains("style="));
        assertFalse(out.contains("onclick"));
        assertFalse(out.contains("javascript:"));
        assertFalse(out.contains("vbscript:"));
        assertFalse(out.contains("onerror"));
        assertTrue(result.remoteImageUrls().isEmpty());
        assertTrue(out.contains("styled") || out.contains("<p"));
    }

    @Test
    void httpImagesAreRewrittenToPlaceholderAndAllowlisted() {
        HtmlSanitizerService.SanitizeResult result =
                sanitizer.sanitize("<img src=\"https://evil.test/pixel.png\" alt=\"x\">");
        assertTrue(result.html().contains(HtmlSanitizerService.PLACEHOLDER_DATA_URI)
                || result.html().contains("data:image/svg+xml"));
        assertFalse(result.html().toLowerCase(Locale.ROOT).contains("https://evil.test/pixel.png"));
        assertTrue(result.remoteImageUrls().contains("https://evil.test/pixel.png"));
    }
}
