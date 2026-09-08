package com.emailtool.ingest;

import com.emailtool.domain.Email;
import com.emailtool.domain.EmailRemoteResource;
import com.emailtool.domain.IngestedFile;
import com.emailtool.normalize.NormalizeService;
import com.emailtool.persist.EmailRemoteResourceRepository;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.IngestedFileRepository;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.time.Instant;
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
    private final TransactionTemplate transactionTemplate;

    public IngestService(
            MailSource mailSource,
            MimeParser mimeParser,
            NormalizeService normalizeService,
            EmailRepository emailRepository,
            IngestedFileRepository ingestedFileRepository,
            EmailRemoteResourceRepository remoteResourceRepository,
            PlatformTransactionManager transactionManager) {
        this.mailSource = mailSource;
        this.mimeParser = mimeParser;
        this.normalizeService = normalizeService;
        this.emailRepository = emailRepository;
        this.ingestedFileRepository = ingestedFileRepository;
        this.remoteResourceRepository = remoteResourceRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IngestStats ingest() {
        int ingested = 0;
        int skipped = 0;
        int failed = 0;
        for (RawMessage raw : mailSource.poll()) {
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
        }
        return new IngestStats(ingested, skipped, failed);
    }

    private boolean persistOne(RawMessage raw) {
        String path = raw.sourceKey();
        var existingFile = ingestedFileRepository.findByPath(path);
        if (existingFile.isPresent() && existingFile.get().getMtimeMillis() == raw.mtimeMillis()) {
            return false;
        }
        try {
            MimeParser.ParsedMime parsed = mimeParser.parse(raw.mimeBytes());
            if (parsed.messageId != null && emailRepository.findByMessageId(parsed.messageId).isPresent()) {
                recordFile(path, raw.mtimeMillis(), parsed.messageId);
                return false;
            }
            Email email = new Email();
            email.setMessageId(parsed.messageId);
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
