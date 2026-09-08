package com.emailtool.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GmailWebLinksTest {

    @Test
    void prefersHexDeepLinkFromGmailMsgId() {
        // Matches JavaMail / Google docs example: 1504408783077114714 → 14e0bbd1484a035a
        String url = GmailWebLinks.url(1504408783077114714L, "<ignored@example.com>");
        assertEquals("https://mail.google.com/mail/u/0/#all/14e0bbd1484a035a", url);
    }

    @Test
    void fallsBackToRfc822Search() {
        String url = GmailWebLinks.url(null, "<abc@mail.example>");
        assertTrue(url.contains("#search/rfc822msgid:"));
        assertTrue(url.contains("abc"));
        assertTrue(url.contains("mail.example"));
    }

    @Test
    void nullWhenNoIds() {
        assertNull(GmailWebLinks.url(null, null));
        assertNull(GmailWebLinks.url(null, "   "));
    }
}
