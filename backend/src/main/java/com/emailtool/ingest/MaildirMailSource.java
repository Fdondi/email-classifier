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
    public PollResult poll(MailPollOptions options) {
        Path root = Path.of(properties.getMail().getMaildir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Mail directory does not exist: {}", root);
            return PollResult.empty();
        }
        List<Path> files = new ArrayList<>();
        Path newDir = root.resolve("new");
        Path curDir = root.resolve("cur");
        if (Files.isDirectory(newDir) || Files.isDirectory(curDir)) {
            collectFiles(newDir, files);
            if (options.includeRead()) {
                collectFiles(curDir, files);
            }
        } else {
            collectFiles(root, files);
        }
        List<RawMessage> messages = new ArrayList<>();
        List<MailboxVisibility> visibility = new ArrayList<>();
        for (Path file : files) {
            try {
                String key = file.toAbsolutePath().normalize().toString();
                messages.add(new RawMessage(
                        key, Files.getLastModifiedTime(file).toMillis(), Files.readAllBytes(file)));
                visibility.add(new MailboxVisibility(key, true));
            } catch (IOException e) {
                log.warn("Skipping unreadable mail file {}: {}", file, e.getMessage());
            }
        }
        if (Files.isDirectory(curDir) && !options.includeRead()) {
            List<Path> readFiles = new ArrayList<>();
            collectFiles(curDir, readFiles);
            for (Path file : readFiles) {
                visibility.add(new MailboxVisibility(file.toAbsolutePath().normalize().toString(), false));
            }
        }
        return new PollResult(messages, visibility);
    }

    private static void collectFiles(Path dir, List<Path> files) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(MaildirMailSource::looksLikeMail)
                    .forEach(files::add);
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
