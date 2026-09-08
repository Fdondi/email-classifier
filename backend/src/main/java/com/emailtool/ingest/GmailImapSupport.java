package com.emailtool.ingest;

import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPMessage;
import org.eclipse.angus.mail.imap.IMAPSSLStore;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.protocol.FetchItem;
import org.eclipse.angus.mail.imap.protocol.FetchResponse;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.util.MailLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gmail IMAP {@code X-GM-MSGID} → web deep link {@code #all/<hex>}.
 * Standard Angus IMAP cannot parse Gmail fetch items without a custom protocol.
 */
public final class GmailImapSupport {

    private static final Logger log = LoggerFactory.getLogger(GmailImapSupport.class);

    static final FetchProfile.Item MSGID_PROFILE_ITEM = new FetchProfile.Item("X-GM-MSGID") {};

    static final FetchItem MSGID_FETCH_ITEM = new FetchItem("X-GM-MSGID", MSGID_PROFILE_ITEM) {
        @Override
        public Object parseItem(FetchResponse response) throws ParsingException {
            return response.readLong();
        }
    };

    private GmailImapSupport() {}

    public static boolean isGmailHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase();
        return h.equals("imap.gmail.com") || h.endsWith(".gmail.com");
    }

    public static Store openStore(Session session, boolean ssl) {
        return ssl ? new GmailImapSslStore(session, null) : new GmailImapPlainStore(session, null);
    }

    /** Lowercase hex of the 64-bit Gmail message id, for {@code mail.google.com/mail/u/0/#all/<hex>}. */
    public static String toWebUrl(long gmailMsgId) {
        return "https://mail.google.com/mail/u/0/#all/" + Long.toHexString(gmailMsgId);
    }

    public static String toWebUrl(Long gmailMsgId) {
        return gmailMsgId == null ? null : toWebUrl(gmailMsgId.longValue());
    }

    /**
     * Prefetch {@code X-GM-MSGID} for messages; returns map keyed by IMAP sequence number.
     * Empty if the folder is not Gmail-capable or fetch fails.
     */
    public static Map<Integer, Long> fetchMsgIds(Folder folder, Message[] messages) {
        Map<Integer, Long> out = new HashMap<>();
        if (messages == null || messages.length == 0 || !(folder instanceof IMAPFolder imapFolder)) {
            return out;
        }
        try {
            FetchProfile profile = new FetchProfile();
            profile.add(MSGID_PROFILE_ITEM);
            imapFolder.fetch(messages, profile);
            for (Message message : messages) {
                if (!(message instanceof IMAPMessage imapMessage)) {
                    continue;
                }
                Object value = imapMessage.getItem(MSGID_FETCH_ITEM);
                if (value instanceof Long id) {
                    out.put(message.getMessageNumber(), id);
                } else if (value instanceof Number number) {
                    out.put(message.getMessageNumber(), number.longValue());
                }
            }
        } catch (MessagingException e) {
            log.warn("Could not fetch Gmail X-GM-MSGID (deep links will fall back to search): {}", e.getMessage());
        }
        return out;
    }

    private static final class GmailImapSslStore extends IMAPSSLStore {
        GmailImapSslStore(Session session, URLName url) {
            super(session, url);
        }

        @Override
        protected IMAPProtocol newIMAPProtocol(String host, int port) throws IOException, ProtocolException {
            return new GmailImapProtocol(name, host, port, session.getProperties(), isSSL, logger);
        }
    }

    private static final class GmailImapPlainStore extends IMAPStore {
        GmailImapPlainStore(Session session, URLName url) {
            super(session, url);
        }

        @Override
        protected IMAPProtocol newIMAPProtocol(String host, int port) throws IOException, ProtocolException {
            return new GmailImapProtocol(name, host, port, session.getProperties(), isSSL, logger);
        }
    }

    private static final class GmailImapProtocol extends IMAPProtocol {
        private static final FetchItem[] ITEMS = {MSGID_FETCH_ITEM};

        GmailImapProtocol(String name, String host, int port, Properties props, boolean isSSL, MailLogger logger)
                throws IOException, ProtocolException {
            super(name, host, port, props, isSSL, logger);
        }

        @Override
        public FetchItem[] getFetchItems() {
            return ITEMS;
        }
    }
}
