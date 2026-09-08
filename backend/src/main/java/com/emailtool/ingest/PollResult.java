package com.emailtool.ingest;

import java.util.List;

public record PollResult(List<RawMessage> messages, List<MailboxVisibility> visibility) {

    public static PollResult empty() {
        return new PollResult(List.of(), List.of());
    }
}
