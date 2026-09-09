package com.emailtool.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import org.junit.jupiter.api.Test;

class DigestServiceActionNeededTest {

    @Test
    void actionNeededFlagPlacesAnyCategoryInActionDigest() {
        Email event = email(Category.EVENT, true);
        Email pay = email(Category.ACTION_NEEDED_PAY, true);
        assertTrue(DigestService.inActionNeededDigest(event));
        assertTrue(DigestService.inActionNeededDigest(pay));
    }

    @Test
    void clearingTheFlagRemovesPayFromActionDigestAndShowsItInOther() {
        Email pay = email(Category.ACTION_NEEDED_PAY, false);
        assertFalse(DigestService.inActionNeededDigest(pay));
        assertTrue(DigestService.inOtherDigest(pay));
    }

    @Test
    void eventWithoutActionStaysOutOfActionAndOther() {
        Email event = email(Category.EVENT, false);
        assertFalse(DigestService.inActionNeededDigest(event));
        assertFalse(DigestService.inOtherDigest(event));
    }

    @Test
    void spamIsNeverInActionDigestEvenIfFlagged() {
        Email spam = email(Category.SPAM, true);
        assertFalse(DigestService.inActionNeededDigest(spam));
    }

    private static Email email(Category category, boolean actionNeeded) {
        Email email = new Email();
        email.setCategory(category);
        email.setActionNeeded(actionNeeded);
        return email;
    }
}
