package com.emailtool.ingest;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class MimeParser {

    public ParsedMime parse(byte[] mimeBytes) throws MessagingException, IOException {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(mimeBytes));
        ParsedMime parsed = new ParsedMime();
        parsed.messageId = trimToNull(message.getMessageID());
        parsed.subject = message.getSubject();
        parsed.dateSent = message.getSentDate() == null ? null : message.getSentDate().toInstant();
        parsed.listUnsubscribe = firstHeader(message, "List-Unsubscribe");
        parsed.authenticationResults = firstHeader(message, "Authentication-Results");
        Address[] from = message.getFrom();
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress internet) {
            parsed.fromAddress = internet.getAddress();
            parsed.fromDisplayName = internet.getPersonal();
        } else if (from != null && from.length > 0) {
            parsed.fromAddress = from[0].toString();
        }
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        if (to != null) {
            List<String> addresses = new ArrayList<>();
            for (Address address : to) {
                if (address instanceof InternetAddress internet) {
                    addresses.add(internet.getAddress());
                } else {
                    addresses.add(address.toString());
                }
            }
            parsed.toAddresses = String.join(",", addresses);
        }
        parsed.charsetOriginal = charsetOf(message);
        collectBodies(message, parsed);
        return parsed;
    }

    private void collectBodies(Part part, ParsedMime parsed) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                collectBodies(bodyPart, parsed);
            }
            return;
        }
        if (part.isMimeType("text/plain") && parsed.textPlain == null) {
            parsed.textPlain = readText(part);
            if (parsed.charsetOriginal == null) {
                parsed.charsetOriginal = charsetOf(part);
            }
            return;
        }
        if (part.isMimeType("text/html") && parsed.textHtml == null) {
            parsed.textHtml = readText(part);
            if (parsed.charsetOriginal == null) {
                parsed.charsetOriginal = charsetOf(part);
            }
        }
    }

    private static String readText(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof byte[] bytes) {
            Charset charset = charsetOrUtf8(charsetOf(part));
            return new String(bytes, charset);
        }
        return content == null ? null : content.toString();
    }

    private static String charsetOf(Part part) throws MessagingException {
        String type = part.getContentType();
        if (type == null) {
            return null;
        }
        String lower = type.toLowerCase();
        int idx = lower.indexOf("charset=");
        if (idx < 0) {
            return null;
        }
        String value = type.substring(idx + 8).trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi);
        }
        return value.trim();
    }

    private static Charset charsetOrUtf8(String name) {
        if (name == null || name.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String firstHeader(MimeMessage message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class ParsedMime {
        public String messageId;
        public String subject;
        public Instant dateSent;
        public String fromAddress;
        public String fromDisplayName;
        public String toAddresses;
        public String listUnsubscribe;
        public String authenticationResults;
        public String textPlain;
        public String textHtml;
        public String charsetOriginal;
    }
}
