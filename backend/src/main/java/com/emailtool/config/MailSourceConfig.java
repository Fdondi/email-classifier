package com.emailtool.config;

import com.emailtool.ingest.ImapMailSource;
import com.emailtool.ingest.MailSource;
import com.emailtool.ingest.MaildirMailSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailSourceConfig {

    @Bean
    MailSource mailSource(AppProperties properties) {
        String source = properties.getMail().getSource();
        if (source == null
                || source.isBlank()
                || "maildir".equalsIgnoreCase(source)
                || "local".equalsIgnoreCase(source)) {
            return new MaildirMailSource(properties);
        }
        if ("imap".equalsIgnoreCase(source)) {
            return new ImapMailSource(properties);
        }
        throw new IllegalStateException("Unknown app.mail.source '" + source + "'. Use imap or maildir.");
    }
}
