package com.emailtool.ingest;

import java.util.Locale;
import java.util.Set;

/**
 * Archived mail must never be triaged. Gmail archive removes messages from INBOX; other
 * providers may use an Archive folder. We only open the configured folder and refuse
 * known archive / all-mail names so a misconfiguration cannot pull them in.
 */
public final class ImapFolderPolicy {

    private static final Set<String> BLOCKED = Set.of(
            "archive",
            "archives",
            "all mail",
            "all-mail",
            "[gmail]/all mail",
            "[gmail]/all-mail",
            "[google mail]/all mail");

    private ImapFolderPolicy() {}

    public static void rejectArchiveFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            throw new IllegalStateException("IMAP folder is not configured");
        }
        String normalized = folder.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (BLOCKED.contains(normalized) || normalized.endsWith("/archive") || normalized.endsWith("/archives")) {
            throw new IllegalStateException(
                    "IMAP folder '"
                            + folder
                            + "' looks like an archive or all-mail mailbox. Use INBOX (or another live folder);"
                            + " archived mail is excluded completely.");
        }
    }
}
