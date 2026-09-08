package com.emailtool.llm;

import java.time.Duration;
import java.util.List;

public interface CommandRunner {

    Result run(List<String> command, Duration timeout);

    record Result(int exitCode, String output) {}
}
