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
    public List<RawMessage> poll() {
        Path root = Path.of(properties.getMail().getMaildir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Mail directory does not exist: {}", root);
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        Path newDir = root.resolve("new");
        Path curDir = root.resolve("cur");
        if (Files.isDirectory(newDir) || Files.isDirectory(curDir)) {
            collectFiles(newDir, files);
            collectFiles(curDir, files);
        } else {
            collectFiles(root, files);
        }
        List<RawMessage> messages = new ArrayList<>();
        for (Path file : files) {
            try {
                messages.add(new RawMessage(
                        file.toAbsolutePath().normalize().toString(),
                        Files.getLastModifiedTime(file).toMillis(),
                        Files.readAllBytes(file)));
            } catch (IOException e) {
                log.warn("Skipping unreadable mail file {}: {}", file, e.getMessage());
            }
        }
        return messages;
    }

    private void collectFiles(Path dir, List<Path> files) {
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
