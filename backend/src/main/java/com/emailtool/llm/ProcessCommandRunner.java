package com.emailtool.llm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessCommandRunner implements CommandRunner {

    @Override
    public Result run(List<String> command, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return new Result(127, e.getMessage());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try {
                process.getInputStream().transferTo(output);
            } catch (IOException e) {
                // Process ended or was destroyed; captured bytes are still returned.
            }
        });
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(Duration.ofSeconds(2).toMillis());
                return new Result(124, "Timed out: " + String.join(" ", command));
            }
            reader.join(Duration.ofSeconds(5).toMillis());
            return new Result(process.exitValue(), output.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Result(130, "Interrupted: " + e.getMessage());
        }
    }
}
