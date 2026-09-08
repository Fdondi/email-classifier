package com.emailtool.ingest;

/**
 * Snapshot of a message still present in the live folder (e.g. INBOX).
 * {@code unread} mirrors the IMAP SEEN flag; {@code inInbox} is always true for
 * rows in this list — absence from the list (within the poll window) means archived/moved.
 */
public record MailboxVisibility(String sourceKey, boolean unread) {}
