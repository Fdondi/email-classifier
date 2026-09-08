package com.emailtool;

import com.emailtool.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class EmailToolApplication {

    private final AppProperties properties;

    public EmailToolApplication(AppProperties properties) {
        this.properties = properties;
    }

    public static void main(String[] args) {
        SpringApplication.run(EmailToolApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDataDir() throws Exception {
        Files.createDirectories(Path.of(properties.getDataDir()));
    }
}
