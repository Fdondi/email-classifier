package com.emailtool.ingest;

public interface MailSource {

    PollResult poll(MailPollOptions options);
}
