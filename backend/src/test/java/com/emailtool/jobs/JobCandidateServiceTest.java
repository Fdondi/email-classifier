package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.persist.EmailRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
    "app.jobs.enabled=true",
    "app.jobs.daily-limit=3",
    "app.jobs.min-interest=60",
    "app.jobs.lookback-hours=36",
    "app.jobs.profile-file=does-not-exist.md"
})
class JobCandidateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T03:30:00Z");

    @Autowired JobCandidateService service;
    @Autowired com.emailtool.config.AppProperties properties;
    @Autowired EmailRepository emailRepository;
    @Autowired JobPostingRepository postingRepository;
    @Autowired JobMailScanRepository scanRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired JobApplicationMailRepository applicationMailRepository;

    @MockitoBean OpportunityQueue queue;
    @MockitoBean JobPostingExtractor extractor;
    @MockitoBean PostingPageFetcher fetcher;

    private Email alertA;
    private Email alertB;
    private Email oldAlert;
    private Email newsletter;

    @BeforeEach
    void setUp() {
        clean();
        alertA = save(Category.JOB_OFFERS, NOW.minus(Duration.ofHours(2)), 0x1a16242043778054L);
        alertB = save(Category.JOB_OFFERS, NOW.minus(Duration.ofHours(5)), null);
        oldAlert = save(Category.JOB_OFFERS, NOW.minus(Duration.ofHours(72)), null);
        newsletter = save(Category.NEWSLETTER_TECH, NOW.minus(Duration.ofHours(1)), null);
        when(queue.latestCv()).thenReturn(Optional.of("Embedded C++ and ML engineer"));
        when(fetcher.fetch(anyString())).thenReturn(new PostingPageFetcher.Result(null, "site forbids automated access"));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void readsRecentJobMailOnceAndSendsTheBestPostingsUnderTheDailyCap() {
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertA.getId())), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.of(List.of(
                        found("Embedded Lead", "https://www.linkedin.com/comm/jobs/view/111?otpToken=SECRET", 90),
                        found("Data Engineer", "https://careers.example.com/jobs/7?utm_source=mail", 70),
                        found("Sales Manager", "https://careers.example.com/jobs/8", 40))));
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertB.getId())), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.of(List.of(
                        found("ML Engineer", "https://jobs.example.org/ml", 85),
                        found("Data Engineer", "https://careers.example.com/jobs/7", 75))));
        when(queue.propose(any())).thenAnswer(inv -> {
            OpportunityQueue.Proposal p = inv.getArgument(0);
            boolean alreadyThere = p.url().equals("https://jobs.example.org/ml");
            return new OpportunityQueue.Outcome("id-" + p.canonicalKey(), !alreadyThere);
        });

        JobCandidateService.RunSummary first = service.run(true, NOW);

        assertThat(first.mailsRead()).isEqualTo(2);
        assertThat(first.postingsFound()).isEqualTo(5);
        assertThat(first.sent()).isEqualTo(2);
        assertThat(first.alreadyInApp()).isEqualTo(1);
        assertThat(first.errors()).isEmpty();
        verify(extractor, never()).extract(argThat(e -> e != null && e.getId().equals(oldAlert.getId())), any(), anyString(), anyString(), anyString(), anyInt());
        verify(extractor, never()).extract(argThat(e -> e != null && e.getId().equals(newsletter.getId())), any(), anyString(), anyString(), anyString(), anyInt());
        verify(extractor, times(2)).extract(any(), any(), eq("CV:\nEmbedded C++ and ML engineer"), eq(""), eq(""), eq(12000));

        ArgumentCaptor<OpportunityQueue.Proposal> sent = ArgumentCaptor.forClass(OpportunityQueue.Proposal.class);
        verify(queue, times(3)).propose(sent.capture());
        List<OpportunityQueue.Proposal> proposals = sent.getAllValues();
        // Best first; the duplicate Data Engineer posting kept its better (75) rating; Sales (40) never goes.
        assertThat(proposals).extracting(OpportunityQueue.Proposal::title)
                .containsExactly("Embedded Lead", "ML Engineer", "Data Engineer");
        OpportunityQueue.Proposal linkedIn = proposals.get(0);
        assertThat(linkedIn.canonicalKey()).isEqualTo("linkedin:111");
        assertThat(linkedIn.url()).isEqualTo("https://www.linkedin.com/jobs/view/111/");
        assertThat(linkedIn.alertMessageId()).isEqualTo("1a16242043778054");
        assertThat(linkedIn.captureText()).isNull();
        assertThat(proposals.get(2).url()).isEqualTo("https://careers.example.com/jobs/7");
        assertThat(proposals.get(2).interestScore()).isEqualTo(75);

        assertThat(postingRepository.findByCanonicalKey("linkedin:111")).get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(JobPosting.Status.SENT);
                    assertThat(p.getCapture()).isEqualTo("link only: site forbids automated access");
                });

        // Same day again: no mail is re-read, one slot is left but nothing else qualifies.
        JobCandidateService.RunSummary second = service.run(true, NOW.plus(Duration.ofHours(1)));
        assertThat(second.mailsRead()).isZero();
        assertThat(second.sent()).isZero();
        verify(queue, times(3)).propose(any());
    }

    @Test
    void capReachedSendsNothingAndFetchedTextGoesAlong() {
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
                found("A", "https://a.example/1", 99),
                found("B", "https://b.example/1", 98),
                found("C", "https://c.example/1", 97),
                found("D", "https://d.example/1", 96))));
        when(fetcher.fetch("https://a.example/1")).thenReturn(new PostingPageFetcher.Result(
                new PostingPageFetcher.Page("https://a.example/1", "Full posting text", "<html></html>"), null));
        when(queue.propose(any())).thenAnswer(inv -> new OpportunityQueue.Outcome("x", true));

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.sent()).isEqualTo(3);
        ArgumentCaptor<OpportunityQueue.Proposal> sent = ArgumentCaptor.forClass(OpportunityQueue.Proposal.class);
        verify(queue, times(3)).propose(sent.capture());
        assertThat(sent.getAllValues().get(0).captureText()).isEqualTo("Full posting text");
        assertThat(postingRepository.findByCanonicalKey("url:https://d.example/1")).get()
                .extracting(JobPosting::getStatus).isEqualTo(JobPosting.Status.PENDING);
    }

    @Test
    void aCompanyIsSuggestedOnlyOncePerMonthWhateverTheSiteOrTitle() {
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
                found("Embedded Lead", "QRZ AG", "https://a.example/1", 90),
                found("ML Engineer", "Brückner", "https://other.example/2", 89),
                found("Data Engineer", "Q.R.Z.", "https://c.example/3", 88),
                found("Tech Lead", "Bruckner GmbH", "https://d.example/4", 80))));
        when(queue.propose(any())).thenAnswer(inv -> new OpportunityQueue.Outcome("x", true));

        // Same run: QRZ goes once, Brückner once; the other QRZ and Brückner postings (other title, site, spelling) wait.
        assertThat(service.run(true, NOW).sent()).isEqualTo(2);
        verify(queue, times(2)).propose(any());
        assertThat(postingRepository.findByCanonicalKey("url:https://c.example/3")).get()
                .extracting(JobPosting::getStatus).isEqualTo(JobPosting.Status.PENDING);

        // 29 days later still held back; after 30 days the company may be suggested again.
        reFind("url:https://c.example/3", NOW.plus(Duration.ofDays(29)));
        assertThat(service.run(false, NOW.plus(Duration.ofDays(29))).sent()).isZero();
        reFind("url:https://c.example/3", NOW.plus(Duration.ofDays(31)));
        assertThat(service.run(false, NOW.plus(Duration.ofDays(31))).sent()).isEqualTo(1);
        assertThat(JobCandidateService.companyKey("Acme S.A.")).isEqualTo(JobCandidateService.companyKey("ACME sa"));
        assertThat(JobCandidateService.companyKey(null)).isEmpty();
    }

    @Test
    void atMostThreeOutsideSwitzerlandAndOneOutsideEuropeOnlyWithoutVisa() {
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
                found("US", "Us", "https://a.example/1", 99, JobPosting.Region.OUTSIDE_EUROPE),
                found("Where", "Where", "https://a.example/2", 98, JobPosting.Region.UNKNOWN),
                found("Remote1", "R1", "https://a.example/3", 97, JobPosting.Region.OUTSIDE_EUROPE_NO_VISA),
                found("Remote2", "R2", "https://a.example/4", 96, JobPosting.Region.OUTSIDE_EUROPE_NO_VISA),
                found("London", "L", "https://a.example/5", 95, JobPosting.Region.EUROPE),
                found("Berlin", "B", "https://a.example/6", 94, JobPosting.Region.EUROPE),
                found("Paris", "P", "https://a.example/7", 93, JobPosting.Region.EUROPE),
                found("Zug", "Z", "https://a.example/8", 92, JobPosting.Region.CH))));
        when(queue.propose(any())).thenAnswer(inv -> new OpportunityQueue.Outcome("x", true));

        properties.getJobs().setDailyLimit(10);
        try {
            service.run(true, NOW);
        } finally {
            properties.getJobs().setDailyLimit(3);
        }

        // US needs a visa, Where is unknown, Remote2 is a second non-Europe one, Paris a fourth outside CH.
        ArgumentCaptor<OpportunityQueue.Proposal> sent = ArgumentCaptor.forClass(OpportunityQueue.Proposal.class);
        verify(queue, times(4)).propose(sent.capture());
        assertThat(sent.getAllValues()).extracting(OpportunityQueue.Proposal::title)
                .containsExactly("Remote1", "London", "Berlin", "Zug");
    }

    @Test
    void aCompanyAppliedToThisMonthWaitsAnEarlierApplicationDoesNotBlock() {
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
                found("Embedded Lead", "Recent AG", "https://a.example/1", 90),
                found("ML Engineer", "Earlier SA", "https://b.example/2", 80))));
        applied("Recent AG", NOW.minus(Duration.ofDays(29)));
        applied("Earlier SA", NOW.minus(Duration.ofDays(31)));
        when(queue.propose(any())).thenAnswer(inv -> new OpportunityQueue.Outcome("x", true));

        assertThat(service.run(true, NOW).sent()).isEqualTo(1);
        verify(queue, times(1)).propose(argThat(p -> p.company().equals("Earlier SA")));
    }

    private void applied(String company, Instant at) {
        JobApplication a = new JobApplication();
        a.company = company;
        a.companyKey = JobCandidateService.companyKey(company);
        a.channel = "direct_ats";
        a.companyType = "employer";
        a.appliedAt = at;
        a.stage = "received";
        a.updatedAt = at;
        applicationRepository.save(a);
    }

    @Test
    void llmFailureLeavesTheMailForTheNextRunAndFirestoreFailureKeepsPostingsPending() {
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertA.getId())), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty());
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertB.getId())), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.of(List.of(found("A", "https://a.example/1", 99))));
        when(queue.propose(any())).thenThrow(new IllegalStateException("No Google credentials"));

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.mailsRead()).isEqualTo(1);
        assertThat(run.sent()).isZero();
        assertThat(run.errors()).anyMatch(e -> e.contains("no usable LLM answer for email " + alertA.getId()))
                .anyMatch(e -> e.contains("No Google credentials"));
        assertThat(scanRepository.existsById(alertA.getId())).isFalse();
        assertThat(postingRepository.findByCanonicalKey("url:https://a.example/1")).get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(JobPosting.Status.PENDING);
                    assertThat(p.getError()).contains("No Google credentials");
                });
    }

    @Test
    void anUnreadableCvLeavesTheMailUnscannedInsteadOfRatingWithoutIt() {
        when(queue.latestCv()).thenThrow(new IllegalStateException("No Google credentials"));

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.mailsRead()).isZero();
        assertThat(run.errors()).anyMatch(e -> e.contains("CV unavailable") && e.contains("No Google credentials"));
        assertThat(scanRepository.count()).isZero();
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void noCvAndNoPreferencesFileAlsoLeavesTheMailUnscanned() {
        when(queue.latestCv()).thenReturn(Optional.empty());

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.errors()).anyMatch(e -> e.contains("no CV in letter-writer"));
        assertThat(scanRepository.count()).isZero();
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void groupsThatStandOutInTheHistoryAreToldToTheModel() {
        List<java.util.Map<String, Object>> ledger = new java.util.ArrayList<>();
        for (int i = 0; i < 46; i++) {
            // 40 companies, half answered; Silentcorp never did, six times.
            boolean silent = i >= 40;
            ledger.add(java.util.Map.of("company", silent ? "Silentcorp" : "Co" + i, "title", "", "outcome", java.util.Map.of("stage", "received"),
                    "ledger", java.util.Map.of("ledger_id", "L" + i, "channel", "direct_ats", "company_type", "employer",
                            "applied_at", "2026-09-01T00:00:00Z", "result_inferred", !silent && i % 2 == 0 ? "rejected" : "ghosted",
                            "reached_human", false, "signal_only", false, "events", List.of())));
        }
        when(queue.ledgerApplications()).thenReturn(ledger);
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of()));

        service.run(true, NOW);

        verify(extractor, times(2)).extract(any(), any(), anyString(), anyString(),
                argThat(h -> h.contains("company \"silentcorp\": 0 of 6 got an answer") && h.contains("σ below")), anyInt());
    }

    @Test
    void withoutTheModelOnlyAlreadyFoundPostingsAreSent() {
        JobCandidateService.RunSummary run = service.run(false, NOW);
        assertThat(run.mailsRead()).isZero();
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    private static JobPostingExtractor.Found found(String title, String url, int score) {
        return found(title, "Co " + title, url, score);
    }

    private static JobPostingExtractor.Found found(String title, String company, String url, int score) {
        return found(title, company, url, score, JobPosting.Region.CH);
    }

    private static JobPostingExtractor.Found found(String title, String company, String url, int score, JobPosting.Region region) {
        return new JobPostingExtractor.Found(title, company, "Zürich", url, "summary", score, "reason", "", region, null);
    }

    private void reFind(String key, Instant at) {
        postingRepository.findByCanonicalKey(key).ifPresent(p -> {
            p.setFoundAt(at);
            postingRepository.save(p);
        });
    }

    private Email save(Category category, Instant sent, Long gmailMsgId) {
        Email email = new Email();
        email.setSourcePath("test-jobs/" + System.nanoTime());
        email.setIngestedAt(sent);
        email.setDateSent(sent);
        email.setCategory(category);
        email.setSubject("Jobs for you");
        email.setTextPlain("see links");
        email.setGmailMsgId(gmailMsgId);
        return emailRepository.save(email);
    }

    private void clean() {
        applicationRepository.deleteAll();
        applicationMailRepository.deleteAll();
        postingRepository.deleteAll();
        scanRepository.deleteAll();
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith("test-jobs/"));
    }
}
