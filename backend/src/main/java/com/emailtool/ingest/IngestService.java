package com.emailtool.ingest;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Email;
import com.emailtool.domain.EmailRemoteResource;
import com.emailtool.domain.IngestedFile;
import com.emailtool.normalize.NormalizeService;
import com.emailtool.persist.EmailRemoteResourceRepository;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.IngestedFileRepository;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final MailSource mailSource;
    private final MimeParser mimeParser;
    private final NormalizeService normalizeService;
    private final EmailRepository emailRepository;
    private final IngestedFileRepository ingestedFileRepository;
    private final EmailRemoteResourceRepository remoteResourceRepository;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;

    public IngestService(
            MailSource mailSource,
            MimeParser mimeParser,
            NormalizeService normalizeService,
            EmailRepository emailRepository,
            IngestedFileRepository ingestedFileRepository,
            EmailRemoteResourceRepository remoteResourceRepository,
            AppProperties properties,
            PlatformTransactionManager transactionManager) {
        this.mailSource = mailSource;
        this.mimeParser = mimeParser;
        this.normalizeService = normalizeService;
        this.emailRepository = emailRepository;
        this.ingestedFileRepository = ingestedFileRepository;
        this.remoteResourceRepository = remoteResourceRepository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IngestStats ingest() {
        return ingest(null);
    }

    public IngestStats ingest(IngestProgressListener progress) {
        PollResult polled = mailSource.poll();
        transactionTemplate.executeWithoutResult(status -> applyMailboxState(polled.visibility()));
        int ingested = 0;
        int skipped = 0;
        int failed = 0;
        for (RawMessage raw : polled.messages()) {
            try {
                Boolean inserted = transactionTemplate.execute(status -> persistOne(raw));
                if (Boolean.TRUE.equals(inserted)) {
                    ingested++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Failed to ingest {}: {}", raw.sourceKey(), e.getMessage());
            }
            if (progress != null) {
                progress.onProgress(ingested, skipped, failed);
            }
        }
        return new IngestStats(ingested, skipped, failed);
    }

    @FunctionalInterface
    public interface IngestProgressListener {
        void onProgress(int ingested, int skipped, int failed);
    }

    private void applyMailboxState(List<MailboxVisibility> visibility) {
        Set<String> presentKeys = new HashSet<>();
        for (MailboxVisibility row : visibility) {
            presentKeys.add(row.sourceKey());
            emailRepository.findBySourcePath(row.sourceKey()).ifPresent(email -> {
                boolean changed = false;
                if (!email.isDigestVisible()) {
                    email.setDigestVisible(true);
                    changed = true;
                }
                if (email.isUnread() != row.unread()) {
                    email.setUnread(row.unread());
                    changed = true;
                }
                if (changed) {
                    emailRepository.save(email);
                }
            });
        }
        if (!"imap".equalsIgnoreCase(properties.getMail().getSource())) {
            return;
        }
        AppProperties.Imap imap = properties.getMail().getImap();
        String prefix = ImapMailSource.folderKeyPrefix(imap);
        Instant windowStart = Instant.now().minus(fillWindow(imap));
        for (Email email : emailRepository.findBySourcePathStartingWith(prefix)) {
            if (presentKeys.contains(email.getSourcePath())) {
                continue;
            }
            Instant when = email.getDateSent() != null ? email.getDateSent() : email.getIngestedAt();
            if (when == null || when.isBefore(windowStart)) {
                continue;
            }
            // Still inside the poll window but gone from the live folder → archived/moved.
            if (email.isDigestVisible()) {
                email.setDigestVisible(false);
                emailRepository.save(email);
            }
        }
    }

    private static Duration fillWindow(AppProperties.Imap imap) {
        int fromRecentHours = (int) Math.ceil(Math.max(1, imap.getRecentHours()) / 24.0);
        return Duration.ofDays(Math.max(fromRecentHours, Math.max(1, imap.getSinceDays())));
    }

    private boolean persistOne(RawMessage raw) {
        String path = raw.sourceKey();
        var existingFile = ingestedFileRepository.findByPath(path);
        if (existingFile.isPresent() && existingFile.get().getMtimeMillis() == raw.mtimeMillis()) {
            backfillGmailMsgId(path, raw.gmailMsgId());
            return false;
        }
        try {
            MimeParser.ParsedMime parsed = mimeParser.parse(raw.mimeBytes());
            if (parsed.messageId != null && emailRepository.findByMessageId(parsed.messageId).isPresent()) {
                emailRepository.findByMessageId(parsed.messageId).ifPresent(existing -> {
                    existing.setUnread(raw.unread());
                    existing.setDigestVisible(true);
                    if (raw.gmailMsgId() != null
                            && (existing.getGmailMsgId() == null
                                    || !existing.getGmailMsgId().equals(raw.gmailMsgId()))) {
                        existing.setGmailMsgId(raw.gmailMsgId());
                    }
                    emailRepository.save(existing);
                });
                recordFile(path, raw.mtimeMillis(), parsed.messageId);
                return false;
            }
            Email email = new Email();
            email.setMessageId(parsed.messageId);
            email.setGmailMsgId(raw.gmailMsgId());
            email.setSourcePath(path);
            email.setIngestedAt(Instant.now());
            email.setFromAddress(parsed.fromAddress);
            email.setFromDisplayName(parsed.fromDisplayName);
            email.setToAddresses(parsed.toAddresses);
            email.setSubject(parsed.subject);
            email.setDateSent(parsed.dateSent);
            email.setListUnsubscribe(parsed.listUnsubscribe);
            email.setAuthenticationResults(parsed.authenticationResults);
            AuthResultsParser.AuthResults auth = AuthResultsParser.parse(parsed.authenticationResults);
            email.setDkimResult(auth.dkim);
            email.setSpfResult(auth.spf);
            email.setDmarcResult(auth.dmarc);
            email.setRawMime(raw.mimeBytes());
            email.setCharsetOriginal(parsed.charsetOriginal);
            email.setDigestVisible(true);
            email.setUnread(raw.unread());
            NormalizeService.Result normalized = normalizeService.normalize(parsed);
            normalizeService.apply(email, normalized);
            Email saved = emailRepository.save(email);
            int index = 0;
            for (String url : normalized.remoteImageUrls()) {
                EmailRemoteResource resource = new EmailRemoteResource();
                resource.setEmail(saved);
                resource.setOriginalUrl(url);
                resource.setResourceIndex(index++);
                remoteResourceRepository.save(resource);
            }
            recordFile(path, raw.mtimeMillis(), parsed.messageId);
            return true;
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void backfillGmailMsgId(String sourcePath, Long gmailMsgId) {
        if (gmailMsgId == null) {
            return;
        }
        emailRepository.findBySourcePath(sourcePath).ifPresent(email -> {
            if (email.getGmailMsgId() == null || !email.getGmailMsgId().equals(gmailMsgId)) {
                email.setGmailMsgId(gmailMsgId);
                emailRepository.save(email);
            }
        });
    }

    private void recordFile(String path, long mtime, String messageId) {
        IngestedFile file = ingestedFileRepository.findByPath(path).orElseGet(IngestedFile::new);
        file.setPath(path);
        file.setMtimeMillis(mtime);
        file.setMessageId(messageId);
        file.setIngestedAt(Instant.now());
        ingestedFileRepository.save(file);
    }

    public record IngestStats(int ingested, int skipped, int failed) {}
}
