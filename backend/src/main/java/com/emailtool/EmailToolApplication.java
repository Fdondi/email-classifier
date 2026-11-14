package com.emailtool;

import com.emailtool.config.AppProperties;
import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
        // G1 never hands grown heap back by default (2.4 GB held while idle after a triage, 93 MB live).
        // A periodic GC after 60 s without one lets it shrink. Set here so every launcher gets it.
        try {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .setVMOption("G1PeriodicGCInterval", "60000");
        } catch (RuntimeException e) {
            // Not a HotSpot G1 JVM: keep its own behaviour.
        }
        // headless(false): the tray icon (com.emailtool.tray) needs AWT. It skips itself if there is no desktop.
        new SpringApplicationBuilder(EmailToolApplication.class).headless(false).run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDataDir() throws Exception {
        Files.createDirectories(Path.of(properties.getDataDir()));
    }
}
