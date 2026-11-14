package com.emailtool.tray;

import com.emailtool.job.TriageJobService;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Windows notification-area icon. Hover: last full run, last check, counts. Click: open the UI.
 * Right-click: menu (open, run triage now, quit). Does nothing when the JVM is headless.
 */
@Component
@ConditionalOnProperty(name = "app.tray.enabled", havingValue = "true", matchIfMissing = true)
public class TrayService {

    private static final Logger log = LoggerFactory.getLogger(TrayService.class);

    private final TrayStatusService statusService;
    private final TriageJobService triageJobService;
    private final ConfigurableApplicationContext context;
    private final URI uiUri;

    private volatile TrayIcon icon;
    private MenuItem lastRunItem;
    private MenuItem lastCheckItem;
    private MenuItem countsItem;
    private MenuItem weekItem;

    public TrayService(
            TrayStatusService statusService,
            TriageJobService triageJobService,
            ConfigurableApplicationContext context,
            @Value("${server.port:8080}") int port) {
        this.statusService = statusService;
        this.triageJobService = triageJobService;
        this.context = context;
        this.uiUri = URI.create("http://localhost:" + port + "/");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            log.info("No system tray available (headless or unsupported); skipping tray icon");
            return;
        }
        TrayStatus status = readStatus();
        EventQueue.invokeLater(() -> install(status));
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void refresh() {
        if (icon == null) {
            return;
        }
        TrayStatus status = readStatus();
        if (status != null) {
            EventQueue.invokeLater(() -> apply(status));
        }
    }

    @PreDestroy
    public void stop() {
        TrayIcon current = icon;
        icon = null;
        if (current != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(current);
        }
    }

    private TrayStatus readStatus() {
        try {
            return statusService.current();
        } catch (RuntimeException e) {
            log.warn("Could not read tray status: {}", e.getMessage());
            return null;
        }
    }

    private void install(TrayStatus status) {
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open email triage");
            open.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
            open.addActionListener(e -> openUi());
            menu.add(open);
            menu.addSeparator();
            lastRunItem = disabledItem();
            lastCheckItem = disabledItem();
            countsItem = disabledItem();
            weekItem = disabledItem();
            menu.add(lastRunItem);
            menu.add(lastCheckItem);
            menu.add(countsItem);
            menu.add(weekItem);
            menu.addSeparator();
            MenuItem run = new MenuItem("Run triage now");
            run.addActionListener(e -> runTriage());
            menu.add(run);
            MenuItem quit = new MenuItem("Quit");
            quit.addActionListener(e -> quit());
            menu.add(quit);

            TrayStatus.Health health = status == null ? TrayStatus.Health.STALE : status.health();
            TrayIcon trayIcon = new TrayIcon(render(health), "Email triage", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        openUi();
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
            icon = trayIcon;
            if (status != null) {
                apply(status);
            }
            log.info("Tray icon installed; click opens {}", uiUri);
        } catch (AWTException | RuntimeException e) {
            log.warn("Could not install tray icon: {}", e.getMessage());
        }
    }

    private void apply(TrayStatus status) {
        TrayIcon current = icon;
        if (current == null) {
            return;
        }
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        current.setToolTip(TrayTooltip.tooltip(status, now, zone));
        current.setImage(render(status.health()));
        lastRunItem.setLabel(TrayTooltip.lastRunLine(status, now, zone));
        lastCheckItem.setLabel(TrayTooltip.lastCheckLine(status, now, zone));
        countsItem.setLabel(TrayTooltip.countsLine(status));
        weekItem.setLabel(TrayTooltip.weekLine(status));
    }

    private static MenuItem disabledItem() {
        MenuItem item = new MenuItem("…");
        item.setEnabled(false);
        return item;
    }

    private void openUi() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uiUri);
            } else {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uiUri.toString()).start();
            }
        } catch (Exception e) {
            log.warn("Could not open {}: {}", uiUri, e.getMessage());
        }
    }

    private void runTriage() {
        new Thread(() -> {
            try {
                triageJobService.startAsync("tray");
            } catch (RuntimeException e) {
                log.warn("Could not start triage from tray: {}", e.getMessage());
            }
            refresh();
        }, "tray-run-triage").start();
    }

    private void quit() {
        new Thread(() -> {
            int code = SpringApplication.exit(context, () -> 0);
            System.exit(code);
        }, "tray-quit").start();
    }

    /** An envelope with a status dot: green = fresh, amber = stale, red = last run failed. */
    static BufferedImage render(TrayStatus.Health health) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x3B82F6));
            g.fillRoundRect(1, 5, 30, 21, 6, 6);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(5, 10, 16, 18);
            g.drawLine(16, 18, 27, 10);
            Color dot = switch (health) {
                case OK -> new Color(0x22C55E);
                case STALE -> new Color(0xF59E0B);
                case FAILING -> new Color(0xEF4444);
            };
            g.setColor(Color.WHITE);
            g.fillOval(17, 15, 15, 15);
            g.setColor(dot);
            g.fillOval(19, 17, 11, 11);
        } finally {
            g.dispose();
        }
        return image;
    }
}
