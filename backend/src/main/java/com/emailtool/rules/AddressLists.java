package com.emailtool.rules;

import com.emailtool.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddressLists {

    private static final Logger log = LoggerFactory.getLogger(AddressLists.class);

    private final AppProperties properties;
    private Set<String> contacts = Set.of();
    private Set<String> billingSenders = Set.of();

    public AddressLists(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void load() {
        Path dir = Path.of(properties.getConfigDir());
        contacts = loadFile(dir.resolve("contacts.txt"));
        billingSenders = loadFile(dir.resolve("billing-senders.txt"));
        log.info("Loaded {} contacts and {} billing senders from {}", contacts.size(), billingSenders.size(), dir);
    }

    public boolean isContact(String address) {
        return address != null && contacts.contains(normalize(address));
    }

    public boolean isBillingSender(String address) {
        return address != null && billingSenders.contains(normalize(address));
    }

    private static Set<String> loadFile(Path path) {
        if (!Files.isRegularFile(path)) {
            log.warn("Address list missing: {}", path);
            return Set.of();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(AddressLists::normalize)
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        } catch (IOException e) {
            log.warn("Could not read {}: {}", path, e.getMessage());
            return Set.of();
        }
    }

    private static String normalize(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }
}
