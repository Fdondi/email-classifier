package com.emailtool.ingest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ImapFolderPolicyTest {

    @Test
    void rejectsArchiveAndAllMailFolders() {
        assertThrows(IllegalStateException.class, () -> ImapFolderPolicy.rejectArchiveFolder("Archive"));
        assertThrows(IllegalStateException.class, () -> ImapFolderPolicy.rejectArchiveFolder("[Gmail]/All Mail"));
        assertThrows(IllegalStateException.class, () -> ImapFolderPolicy.rejectArchiveFolder("INBOX/Archive"));
    }

    @Test
    void allowsInbox() {
        assertDoesNotThrow(() -> ImapFolderPolicy.rejectArchiveFolder("INBOX"));
        assertDoesNotThrow(() -> ImapFolderPolicy.rejectArchiveFolder("INBOX.Work"));
    }
}
