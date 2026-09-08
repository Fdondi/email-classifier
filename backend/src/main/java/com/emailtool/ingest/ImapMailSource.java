package com.emailtool.ingest;

import com.emailtool.config.AppProperties;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImapMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(ImapMailSource.class);

    private final AppProperties properties;

    public ImapMailSource(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public PollResult poll() {
        AppProperties.Imap imap = properties.getMail().getImap();
        if (imap.getUsername() == null
                || imap.getUsername().isBlank()
                || imap.getPassword() == null
                || imap.getPassword().isBlank()
                || imap.getPassword().startsWith("replace-")) {
            throw new IllegalStateException(
                    "IMAP is selected but username/password are missing. Copy config/secrets.example.yml to config/secrets.yml and put your mail host, address, and app password there. Thunderbird is not required and does not expose a local API.");
        }
        ImapFolderPolicy.rejectArchiveFolder(imap.getFolder());
        String protocol = imap.isSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", imap.getHost());
        props.put("mail." + protocol + ".port", Integer.toString(imap.getPort()));
        props.put("mail." + protocol + ".ssl.enable", Boolean.toString(imap.isSsl()));
        props.put("mail." + protocol + ".ssl.checkserveridentity", "true");
        props.put("mail." + protocol + ".partialfetch", "false");
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "60000");
        Session session = Session.getInstance(props);
        Store store = null;
        Folder folder = null;
        try {
            boolean gmail = GmailImapSupport.isGmailHost(imap.getHost());
            store = gmail ? GmailImapSupport.openStore(session, imap.isSsl()) : session.getStore(protocol);
            store.connect(imap.getHost(), imap.getPort(), imap.getUsername(), imap.getPassword());
            folder = store.getFolder(imap.getFolder());
            if (folder == null || !folder.exists()) {
                throw new IllegalStateException("IMAP folder not found: " + imap.getFolder());
            }
            folder.open(Folder.READ_ONLY);
            Message[] found = search(folder, imap);
            FetchProfile flagProfile = new FetchProfile();
            flagProfile.add(FetchProfile.Item.FLAGS);
            flagProfile.add(FetchProfile.Item.ENVELOPE);
            if (found.length > 0) {
                folder.fetch(found, flagProfile);
            }
            Instant now = Instant.now();
            Duration recent = Duration.ofHours(Math.max(1, imap.getRecentHours()));
            Duration fill = fillWindow(imap);
            UIDFolder uidFolder = folder instanceof UIDFolder uids ? uids : null;
            List<DatedMessage> dated = new ArrayList<>();
            List<MailboxVisibility> visibility = new ArrayList<>();
            for (Message message : found) {
                boolean unread = !message.isSet(Flags.Flag.SEEN);
                String key = sourceKey(imap, uidFolder, message);
                visibility.add(new MailboxVisibility(key, unread));
                dated.add(new DatedMessage(message, sortDate(message).toInstant(), unread, key));
            }
            // Download and rate everything still in the live folder; UI filters read vs unread.
            List<DatedMessage> picked = ImapWindow.select(
                    dated, DatedMessage::when, now, recent, fill, Math.max(0, imap.getMaxMessages()));
            int recentCount = 0;
            int unreadCount = 0;
            Instant recentSince = now.minus(recent);
            for (DatedMessage row : picked) {
                if (!row.when().isBefore(recentSince)) {
                    recentCount++;
                }
                if (row.unread()) {
                    unreadCount++;
                }
            }
            List<RawMessage> result = new ArrayList<>();
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.CONTENT_INFO);
            Message[] toFetch = picked.stream().map(DatedMessage::message).toArray(Message[]::new);
            if (toFetch.length > 0) {
                folder.fetch(toFetch, profile);
            }
            Map<Integer, Long> gmailMsgIds =
                    gmail ? GmailImapSupport.fetchMsgIds(folder, toFetch) : Map.of();
            for (DatedMessage row : picked) {
                Message message = row.message();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                message.writeTo(buffer);
                Long gmailMsgId = gmailMsgIds.get(message.getMessageNumber());
                result.add(new RawMessage(
                        row.sourceKey(),
                        row.when().toEpochMilli(),
                        buffer.toByteArray(),
                        row.unread(),
                        gmailMsgId));
            }
            log.info(
                    "IMAP fetched {} messages ({} unread, {} from last {}h, {} older within {}d) from {}/{}",
                    result.size(),
                    unreadCount,
                    recentCount,
                    recent.toHours(),
                    result.size() - recentCount,
                    fill.toDays(),
                    imap.getHost(),
                    imap.getFolder());
            return new PollResult(result, visibility);
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("IMAP download failed: " + e.getMessage(), e);
        } finally {
            close(folder, store);
        }
    }

    private Message[] search(Folder folder, AppProperties.Imap imap) throws MessagingException {
        Date since = Date.from(Instant.now().minus(fillWindow(imap)));
        SearchTerm byDate = new ReceivedDateTerm(ComparisonTerm.GE, since);
        return folder.search(byDate);
    }

    static String folderKeyPrefix(AppProperties.Imap imap) {
        return "imap://" + imap.getUsername() + "@" + imap.getHost() + "/" + imap.getFolder() + ";UID=";
    }

    private static String sourceKey(AppProperties.Imap imap, UIDFolder uidFolder, Message message)
            throws MessagingException {
        long uid = uidFolder == null ? message.getMessageNumber() : uidFolder.getUID(message);
        return folderKeyPrefix(imap) + uid;
    }

    private static Duration fillWindow(AppProperties.Imap imap) {
        int fromRecentHours = (int) Math.ceil(Math.max(1, imap.getRecentHours()) / 24.0);
        return Duration.ofDays(Math.max(fromRecentHours, Math.max(1, imap.getSinceDays())));
    }

    private static Date sortDate(Message message) {
        try {
            if (message.getReceivedDate() != null) {
                return message.getReceivedDate();
            }
            if (message.getSentDate() != null) {
                return message.getSentDate();
            }
        } catch (MessagingException e) {
            return Date.from(Instant.EPOCH);
        }
        return Date.from(Instant.EPOCH);
    }

    private record DatedMessage(Message message, Instant when, boolean unread, String sourceKey) {}

    private static void close(Folder folder, Store store) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException e) {
                log.warn("Could not close IMAP folder: {}", e.getMessage());
            }
        }
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                log.warn("Could not close IMAP store: {}", e.getMessage());
            }
        }
    }
}
