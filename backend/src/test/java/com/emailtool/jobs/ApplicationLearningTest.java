package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.persist.EmailRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApplicationLearningTest {

    private static final Instant NOW = Instant.parse("2026-11-26T03:30:00Z");
    private static final String BACKFILL_AT = "2026-11-20T09:00:00.123000Z";

    @Autowired ApplicationLearning learning;
    @Autowired JobApplicationRepository applications;
    @Autowired JobApplicationMailRepository mails;
    @Autowired EmailRepository emailRepository;

    @MockitoBean OpportunityQueue queue;
    @MockitoBean OutcomeMailReader reader;

    @BeforeEach
    void setUp() {
        clean();
        when(queue.learning()).thenReturn(Map.of(
                "sender_rules", Map.of("items", List.of(
                        Map.of("sender", "jobs-noreply@linkedin.com", "subject", "^.*your application was sent to (?P<company>.+)$",
                                "kind", "applied", "channel", "linkedin_easy_apply"),
                        Map.of("sender_domain", "myworkday.com", "subject", "(Regarding your application|Cancelled)", "kind", "rejection"),
                        Map.of("sender_domain", "emeritus.org", "kind", "ignore")))));
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            ledger.add(backfilled("Talentfinder AG", "linkedin_easy_apply", "agency", "applied", "ghosted", "b" + i));
        }
        ledger.add(backfilled("Acme Group", "direct_ats", "employer", "received", "pending", "a1"));
        when(queue.ledgerApplications()).thenReturn(ledger);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void outcomeMailUpdatesTheApplicationsAndSavesTheCounts() {
        mail("jobs-noreply@linkedin.com", "Alex, your application was sent to Talentfinder AG", Category.JOB_OFFERS, 1, 0x100L);
        Email workday = mail("acme@myworkday.com", "Regarding your application", Category.OTHER, 2, 0x200L);
        mail("jobs@acme.com", "Your application", Category.JOB_INTERVIEWS, 3, 0xa1L); // already in the backfill
        mail("news@example.com", "Weekly digest", Category.NEWSLETTER_TECH, 1, 0x300L);
        mail("x@emeritus.org", "Application deadline", Category.PROMOTION, 1, 0x400L);
        mail("bob@example.com", "Hello", Category.JOB_INTERVIEWS, 1, 0x500L);
        mail("old@example.com", "Interview", Category.JOB_INTERVIEWS, 20, 0x600L); // before the window
        when(reader.read(argThat(e -> e != null && e.getId().equals(workday.getId())), anyInt())).thenReturn(Optional.of(
                new OutcomeMailReader.Event("rejection", "Acme", "", "Engineer", "employer", false, "Not proceeding", null)));
        when(reader.read(argThat(e -> e != null && "Hello".equals(e.getSubject())), anyInt())).thenReturn(Optional.of(
                new OutcomeMailReader.Event("not_application", "", "", "", "employer", false, "", null)));

        List<String> errors = new ArrayList<>();
        learning.refresh(NOW, true, errors);

        assertThat(errors).isEmpty();
        assertThat(applications.count()).isEqualTo(6);
        assertThat(learning.overview().changes()).extracting(JobApplicationMail::getChange).containsExactly(
                "Talentfinder AG: applied — new application (linkedin_easy_apply, agency). There: 5 applications, 0 answered,"
                        + " 0 reached screening.",
                "Acme Group: rejection — rejected. There: 1 applications, 1 answered, 0 reached screening.");
        assertThat(mails.findById("400")).get().extracting(JobApplicationMail::getKind).isEqualTo("ignore");
        assertThat(mails.findById("500")).get().extracting(JobApplicationMail::getKind).isEqualTo("not_application");
        assertThat(mails.existsById("300")).isFalse();
        assertThat(mails.existsById("600")).isFalse();
        verify(reader, never()).read(argThat(e -> e != null && "Your application".equals(e.getSubject())), anyInt());
        verify(reader, never()).read(argThat(e -> e != null && "Weekly digest".equals(e.getSubject())), anyInt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> saved = ArgumentCaptor.forClass(Map.class);
        verify(queue).updateLearning(saved.capture());
        assertThat(saved.getValue().get("counterparties.items")).asList().first()
                .isEqualTo(Map.of("company_key", "talentfinder", "applications", 5, "reached_human", 0, "alpha", 1, "beta", 6, "p_human", 0.1429));
        @SuppressWarnings("unchecked")
        Map<String, Object> directAts = (Map<String, Object>) ((Map<?, ?>) saved.getValue().get("segments.by_channel")).get("direct_ats");
        assertThat(directAts).containsEntry("n", 1).containsEntry("rejected", 1).containsEntry("p_screen", 0.3333);
        assertThat(saved.getValue()).containsEntry("prompt_notes", ""); // six applications: nothing stands out yet

        // Next run: every mail was read, nothing changes, Firestore is left alone.
        learning.refresh(NOW.plus(Duration.ofHours(1)), true, errors);
        verify(queue, times(1)).updateLearning(org.mockito.ArgumentMatchers.any());
        verify(reader, times(1)).read(argThat(e -> e != null && e.getId().equals(workday.getId())), anyInt());
        assertThat(applications.count()).isEqualTo(6);
    }

    @Test
    void fewSilencesBarelyMoveACompanyManyDo() {
        // Roughly the real history: 575 applications, 31% got an answer.
        List<JobApplication> all = new ArrayList<>();
        for (int i = 0; i < 550; i++) {
            all.add(app("c" + i, i < 177));
        }
        for (int i = 0; i < 5; i++) {
            all.add(app("Quiet", false));
        }
        for (int i = 0; i < 20; i++) {
            all.add(app("Silentcorp", i == 0));
        }

        assertThat(ApplicationLearning.signals(all)).extracting(ApplicationLearning.Signal::text).containsExactly(
                "company \"silentcorp\": 1 of 20 got an answer (a rejection counts) → estimated 18.0% vs 31.0% average (2.2σ below)");
        assertThat(ApplicationLearning.notes(all))
                .startsWith("Of the person's 575 past applications, on average 31.0% got an answer (a rejection counts) and 0.0%"
                        + " reached a screening or interview.")
                .endsWith("\n- company \"silentcorp\": 1 of 20 got an answer (a rejection counts) → estimated 18.0% vs 31.0%"
                        + " average (2.2σ below)");
    }

    @Test
    void theSilenceThatCrossesOneSigmaIsReportedAsTheModelNowBeingTold() {
        List<JobApplication> all = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            all.add(app("c" + i, i < 20));
        }
        for (int i = 0; i < 5; i++) {
            all.add(app("Silentcorp", false));
        }
        EmailRepository.MailHead head = new EmailRepository.MailHead() {
            public Long getId() { return 1L; }
            public Long getGmailMsgId() { return null; }
            public String getFromAddress() { return "jobs@silentcorp.example"; }
            public String getSubject() { return "Application received"; }
            public Instant getDateSent() { return NOW; }
            public Category getCategory() { return Category.OTHER; }
        };

        assertThat(learning.apply(new OutcomeMailReader.Event("applied", "Silentcorp", "", "", "", false, "Applied", "direct_ats"),
                "email:1", head, NOW, all)).isTrue();

        assertThat(mails.findById("email:1")).get().extracting(JobApplicationMail::getChange).isEqualTo(
                "Silentcorp: applied — new application (direct_ats, employer). There: 6 applications,"
                        + " 0 answered, 0 reached screening; model now told: company \"silentcorp\": 0 of 6 got an answer"
                        + " (a rejection counts) → estimated 33.4% vs 43.5% average (1.1σ below).");
    }

    @Test
    void withoutTheModelOnlySenderRulesAreReadAndTheRestWaits() {
        mail("acme@myworkday.com", "Regarding your application", Category.OTHER, 1, 0x200L);

        learning.refresh(NOW, false, new ArrayList<>());

        assertThat(mails.existsById("200")).isFalse();
        verify(reader, never()).read(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    /** Every one the same channel and type, so only the company can stand out. */
    private static JobApplication app(String company, boolean answered) {
        JobApplication a = new JobApplication();
        a.company = company;
        a.companyKey = JobCandidateService.companyKey(company);
        a.channel = "direct_ats";
        a.companyType = "employer";
        a.appliedAt = NOW.minus(Duration.ofDays(60));
        a.stage = "received";
        a.result = answered ? "rejected" : null;
        return a;
    }

    private static Map<String, Object> backfilled(String company, String channel, String type, String stage, String result, String mailId) {
        return Map.of(
                "company", company,
                "title", "",
                "outcome", Map.of("stage", stage),
                "ledger", Map.of(
                        "ledger_id", "L-" + mailId, "channel", channel, "company_type", type, "applied_at", BACKFILL_AT,
                        "result_inferred", result, "reached_human", false, "signal_only", false,
                        "events", List.of(Map.of("mail_id", mailId, "at", BACKFILL_AT, "kind", stage, "summary", "backfilled"))));
    }

    private Email mail(String from, String subject, Category category, int daysAgo, Long gmailMsgId) {
        Email email = new Email();
        email.setSourcePath("test-learning/" + System.nanoTime());
        Instant sent = NOW.minus(Duration.ofDays(daysAgo));
        email.setIngestedAt(sent);
        email.setDateSent(sent);
        email.setCategory(category);
        email.setFromAddress(from);
        email.setSubject(subject);
        email.setTextPlain("body");
        email.setGmailMsgId(gmailMsgId);
        return emailRepository.save(email);
    }

    private void clean() {
        applications.deleteAll();
        mails.deleteAll();
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith("test-learning/"));
    }
}
