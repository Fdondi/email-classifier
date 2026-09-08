package com.emailtool.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class BuildVersion {

    private final String version;

    public BuildVersion() {
        this.version = load();
    }

    public String get() {
        return version;
    }

    private static String load() {
        try (InputStream in = BuildVersion.class.getResourceAsStream("/build-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty("version");
            if (value == null || value.isBlank()) {
                return "unknown";
            }
            return value.strip();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
