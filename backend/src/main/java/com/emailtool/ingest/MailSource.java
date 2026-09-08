package com.emailtool.ingest;

import java.util.List;

public interface MailSource {

    List<RawMessage> poll();
}
