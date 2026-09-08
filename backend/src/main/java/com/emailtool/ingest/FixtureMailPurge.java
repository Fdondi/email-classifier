package com.emailtool.ingest;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Email;
import com.emailtool.domain.IngestedFile;
import com.emailtool.persist.EmailRemoteResourceRepository;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.IngestedFileRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FixtureMailPurge {

    private static final Logger log = LoggerFactory.getLogger(FixtureMailPurge.class);

    private final AppProperties properties;
    private final EmailRepository emailRepository;
    private final EmailRemoteResourceRepository remoteResourceRepository;
    private final IngestedFileRepository ingestedFileRepository;

    public FixtureMailPurge(
            AppProperties properties,
            EmailRepository emailRepository,
            EmailRemoteResourceRepository remoteResourceRepository,
            IngestedFileRepository ingestedFileRepository) {
        this.properties = properties;
        this.emailRepository = emailRepository;
        this.remoteResourceRepository = remoteResourceRepository;
        this.ingestedFileRepository = ingestedFileRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void purgeSyntheticFixturesWhenUsingImap() {
        String source = properties.getMail().getSource();
        if (source == null || !"imap".equalsIgnoreCase(source)) {
            return;
        }
        List<Email> fixtures = emailRepository.findAll().stream().filter(FixtureMail::isFixture).toList();
        for (Email email : fixtures) {
            remoteResourceRepository.deleteByEmailId(email.getId());
        }
        emailRepository.deleteAll(fixtures);
        List<IngestedFile> files = ingestedFileRepository.findAll().stream()
                .filter(file -> FixtureMail.isFixturePath(file.getPath()))
                .toList();
        ingestedFileRepository.deleteAll(files);
        if (!fixtures.isEmpty() || !files.isEmpty()) {
            log.info(
                    "Removed {} synthetic fixture emails and {} testdata ingest records from the IMAP database",
                    fixtures.size(),
                    files.size());
        }
    }
}
