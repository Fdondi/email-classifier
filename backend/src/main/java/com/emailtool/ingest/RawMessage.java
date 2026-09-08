package com.emailtool.ingest;

public record RawMessage(
        String sourceKey, long mtimeMillis, byte[] mimeBytes, boolean unread, Long gmailMsgId) {

    public RawMessage(String sourceKey, long mtimeMillis, byte[] mimeBytes) {
        this(sourceKey, mtimeMillis, mimeBytes, true, null);
    }

    public RawMessage(String sourceKey, long mtimeMillis, byte[] mimeBytes, boolean unread) {
        this(sourceKey, mtimeMillis, mimeBytes, unread, null);
    }
}
