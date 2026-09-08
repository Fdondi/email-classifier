package com.emailtool.ingest;

import com.emailtool.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaildirMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(MaildirMailSource.class);

    private final AppProperties properties;

    public MaildirMailSource(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public PollResult poll() {
        Path root = Path.of(properties.getMail().getMaildir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Mail directory does not exist: {}", root);
            return PollResult.empty();
        }
        List<RawMessage> messages = new ArrayList<>();
        List<MailboxVisibility> visibility = new ArrayList<>();
        Path newDir = root.resolve("new");
        Path curDir = root.resolve("cur");
        if (Files.isDirectory(newDir) || Files.isDirectory(curDir)) {
            collect(newDir, true, messages, visibility);
            collect(curDir, false, messages, visibility);
        } else {
            collect(root, true, messages, visibility);
        }
        return new PollResult(messages, visibility);
    }

    private static void collect(
            Path dir, boolean unread, List<RawMessage> messages, List<MailboxVisibility> visibility) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(MaildirMailSource::looksLikeMail)
                    .forEach(file -> {
                        try {
                            String key = file.toAbsolutePath().normalize().toString();
                            messages.add(new RawMessage(
                                    key,
                                    Files.getLastModifiedTime(file).toMillis(),
                                    Files.readAllBytes(file),
                                    unread));
                            visibility.add(new MailboxVisibility(key, unread));
                        } catch (IOException e) {
                            log.warn("Skipping unreadable mail file {}: {}", file, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not list {}: {}", dir, e.getMessage());
        }
    }

    private static boolean looksLikeMail(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".")) {
            return false;
        }
        return name.endsWith(".eml") || !name.contains(".");
    }
}
