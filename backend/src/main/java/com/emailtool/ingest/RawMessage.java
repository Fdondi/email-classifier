package com.emailtool.ingest;

public record RawMessage(String sourceKey, long mtimeMillis, byte[] mimeBytes) {}
