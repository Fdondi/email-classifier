package com.emailtool.api;

import com.emailtool.ingest.GmailImapSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds Gmail web URLs from stored IMAP / RFC822 identifiers. */
public final class GmailWebLinks {

    private GmailWebLinks() {}

    /**
     * Prefer {@code #all/<hex>} from X-GM-MSGID (opens the message). Fall back to rfc822msgid search.
     */
    public static String url(Long gmailMsgId, String rfc822MessageId) {
        String direct = GmailImapSupport.toWebUrl(gmailMsgId);
        if (direct != null) {
            return direct;
        }
        return searchByRfc822(rfc822MessageId);
    }

    public static String searchByRfc822(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        String id = messageId.strip();
        if (id.startsWith("<") && id.endsWith(">") && id.length() > 2) {
            id = id.substring(1, id.length() - 1);
        }
        if (id.isBlank()) {
            return null;
        }
        return "https://mail.google.com/mail/u/0/#search/rfc822msgid:"
                + URLEncoder.encode(id, StandardCharsets.UTF_8);
    }
}
