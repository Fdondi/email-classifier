package com.emailtool.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.domain.Email;
import org.junit.jupiter.api.Test;

class FixtureMailTest {

    @Test
    void detectsTestdataPathAndFakeSubject() {
        Email fromFiles = new Email();
        fromFiles.setSourcePath("C:\\Users\\franc\\Documents\\GitHub\\email-tool\\testdata\\eml\\01-billing.eml");
        fromFiles.setSubject("Invoice");
        assertTrue(FixtureMail.isFixture(fromFiles));

        Email labeled = new Email();
        labeled.setSourcePath("imap://user@imap.gmail.com/INBOX;UID=12");
        labeled.setSubject("FAKE TEST DATA lunch");
        assertTrue(FixtureMail.isFixture(labeled));

        Email real = new Email();
        real.setSourcePath("imap://user@imap.gmail.com/INBOX;UID=99");
        real.setSubject("Your invoice from the city");
        assertFalse(FixtureMail.isFixture(real));
    }
}
