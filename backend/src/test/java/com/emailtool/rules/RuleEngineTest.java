package com.emailtool.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleEngineTest {

    @TempDir
    Path tempDir;

    private RuleEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("contacts.txt"), "friend@local.test\n");
        Files.writeString(tempDir.resolve("billing-senders.txt"), "billing@invoices.test\n");
        AppProperties properties = new AppProperties();
        properties.setConfigDir(tempDir.toString());
        AddressLists lists = new AddressLists(properties);
        lists.load();
        engine = new RuleEngine(List.of(
                new KnownBillingSenderRule(lists), new AuthFailureRule(lists)));
    }

    @Test
    void billingSenderBecomesActionPay() {
        Email email = new Email();
        email.setFromAddress("billing@invoices.test");
        assertTrue(engine.classify(email));
        assertEquals(Category.ACTION_NEEDED_PAY, email.getCategory());
        assertEquals(ClassificationSource.RULE, email.getClassificationSource());
    }

    @Test
    void listUnsubscribeDoesNotForceNewsletter() {
        Email email = new Email();
        email.setFromAddress("news@newsletters.test");
        email.setListUnsubscribe("<https://newsletters.test/unsub>");
        assertFalse(engine.classify(email));
        assertEquals(null, email.getCategory());
    }

    @Test
    void authFailBecomesSpam() {
        Email email = new Email();
        email.setFromAddress("winner@spam.test");
        email.setAuthenticationResults("mx; dkim=fail; spf=fail; dmarc=fail");
        email.setDkimResult("fail");
        email.setSpfResult("fail");
        email.setDmarcResult("fail");
        assertTrue(engine.classify(email));
        assertEquals(Category.SPAM, email.getCategory());
    }

    @Test
    void contactIsExemptFromNewsletterAndAuthRules() {
        Email email = new Email();
        email.setFromAddress("friend@local.test");
        email.setListUnsubscribe("<https://evil.test/unsub>");
        email.setAuthenticationResults("mx; dkim=fail; spf=fail; dmarc=fail");
        email.setDkimResult("fail");
        email.setSpfResult("fail");
        email.setDmarcResult("fail");
        assertFalse(engine.classify(email));
    }
}
