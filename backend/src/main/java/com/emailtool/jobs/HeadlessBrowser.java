package com.emailtool.jobs;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Edge (or Chrome) in headless mode, signed out: InPrivate, a new empty profile per page (no cookies,
 * nothing kept), extensions off. Prints the DOM once the page's scripts have run.
 */
public class HeadlessBrowser implements PostingPageFetcher.Renderer {

    static final long TIMEOUT_SECONDS = 60;

    private final String executable;

    public HeadlessBrowser(String executable) {
        this.executable = executable;
    }

    @Override
    public String render(String url) throws IOException, InterruptedException {
        String scheme = URI.create(url).getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("not a web address: " + url);
        }
        Path dir = Files.createTempDirectory("email-tool-browser");
        Path dom = dir.resolve("dom.html");
        try {
            Process browser = new ProcessBuilder(
                    executable,
                    "--headless=new",
                    "--edge-skip-compat-layer-relaunch", // Edge's relaunch would drop the DOM printed on stdout
                    "--inprivate",
                    "--no-first-run",
                    "--disable-extensions",
                    "--user-data-dir=" + dir.resolve("profile"),
                    "--virtual-time-budget=10000",
                    "--dump-dom",
                    "--", // the URL can never be read as a switch
                    url)
                    .redirectOutput(dom.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!browser.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                browser.descendants().forEach(ProcessHandle::destroyForcibly);
                browser.destroyForcibly();
                throw new IOException("browser timed out");
            }
            // Edge sometimes crashes on exit after printing the DOM: judge by the output, not the exit code.
            long size = Files.size(dom);
            if (size == 0) {
                throw new IOException("browser printed no page (exit " + browser.exitValue() + ")");
            }
            if (size > PostingPageFetcher.MAX_PAGE_BYTES) {
                throw new IOException("page too large");
            }
            return Files.readString(dom, StandardCharsets.UTF_8);
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Each profile is 10-40 MB; a file the browser still holds is left for the OS temp cleanup. */
    private static void deleteQuietly(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }
}
