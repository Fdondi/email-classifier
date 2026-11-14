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
    @Autowired EmailRepository emailRepository;
    @Autowired JobPostingRepository postingRepository;
    @Autowired JobMailScanRepository scanRepository;

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
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertA.getId())), any(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.of(List.of(
                        found("Embedded Lead", "https://www.linkedin.com/comm/jobs/view/111?otpToken=SECRET", 90),
                        found("Data Engineer", "https://careers.example.com/jobs/7?utm_source=mail", 70),
                        found("Sales Manager", "https://careers.example.com/jobs/8", 40))));
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertB.getId())), any(), anyString(), anyString(), anyInt()))
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
        verify(extractor, never()).extract(argThat(e -> e != null && e.getId().equals(oldAlert.getId())), any(), anyString(), anyString(), anyInt());
        verify(extractor, never()).extract(argThat(e -> e != null && e.getId().equals(newsletter.getId())), any(), anyString(), anyString(), anyInt());
        verify(extractor, times(2)).extract(any(), any(), eq("CV:\nEmbedded C++ and ML engineer"), eq(""), eq(12000));

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
        when(extractor.extract(any(), any(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
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
    void llmFailureLeavesTheMailForTheNextRunAndFirestoreFailureKeepsPostingsPending() {
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertA.getId())), any(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty());
        when(extractor.extract(argThat(e -> e != null && e.getId().equals(alertB.getId())), any(), anyString(), anyString(), anyInt()))
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
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void noCvAndNoPreferencesFileAlsoLeavesTheMailUnscanned() {
        when(queue.latestCv()).thenReturn(Optional.empty());

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.errors()).anyMatch(e -> e.contains("no CV in letter-writer"));
        assertThat(scanRepository.count()).isZero();
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void withoutTheModelOnlyAlreadyFoundPostingsAreSent() {
        JobCandidateService.RunSummary run = service.run(false, NOW);
        assertThat(run.mailsRead()).isZero();
        verify(extractor, never()).extract(any(), any(), anyString(), anyString(), anyInt());
    }

    private static JobPostingExtractor.Found found(String title, String url, int score) {
        return new JobPostingExtractor.Found(title, "Acme", "Zürich", url, "summary", score, "reason", "");
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
        postingRepository.deleteAll();
        scanRepository.deleteAll();
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith("test-jobs/"));
    }
}
