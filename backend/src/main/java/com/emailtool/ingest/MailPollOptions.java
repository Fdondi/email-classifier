package com.emailtool.ingest;

public record MailPollOptions(boolean includeRead) {

    public static MailPollOptions defaults() {
        return new MailPollOptions(false);
    }
}
