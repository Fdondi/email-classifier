package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

/** Career tracks: each fills its own quota first, the general list takes what is left of the daily limit. */
@SpringBootTest
@TestPropertySource(properties = {
    "app.jobs.enabled=true",
    "app.jobs.daily-limit=5",
    "app.jobs.min-interest=60",
    "app.jobs.lookback-hours=36",
    "app.jobs.profile-file=does-not-exist.md"
})
class JobCandidateTracksTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:30:00Z");

    @Autowired JobCandidateService service;
    @Autowired EmailRepository emailRepository;
    @Autowired JobPostingRepository postingRepository;
    @Autowired JobMailScanRepository scanRepository;

    @MockitoBean OpportunityQueue queue;
    @MockitoBean JobPostingExtractor extractor;
    @MockitoBean PostingPageFetcher fetcher;

    @BeforeEach
    void setUp() {
        clean();
        Email alert = new Email();
        alert.setSourcePath("test-tracks/" + System.nanoTime());
        alert.setIngestedAt(NOW.minus(Duration.ofHours(1)));
        alert.setDateSent(NOW.minus(Duration.ofHours(1)));
        alert.setCategory(Category.JOB_OFFERS);
        alert.setSubject("Jobs for you");
        alert.setTextPlain("see links");
        emailRepository.save(alert);
        when(queue.latestCv()).thenReturn(Optional.of("Embedded C++ and ML engineer"));
        when(fetcher.fetch(anyString())).thenReturn(new PostingPageFetcher.Result(null, "site forbids automated access"));
        when(queue.propose(any())).thenAnswer(inv -> new OpportunityQueue.Outcome("x", true));
        when(queue.jobTracks()).thenReturn(List.of(
                new OpportunityQueue.Track("alpha", 2, "Alpha notes"),
                new OpportunityQueue.Track("beta", 1, "Beta notes")));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void eachTrackFillsItsQuotaFirstAndTheGeneralListTakesTheRest() {
        when(extractor.extract(any(), any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.of(List.of(
                found("G99", 99, ""),
                found("G98", 98, ""),
                found("G97", 97, "astro"),
                found("A95", 95, "ALPHA"),
                found("A90", 90, "alpha"),
                found("A85", 85, "alpha"),
                found("B80", 80, "beta"),
                found("B75", 75, "beta"))));

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.errors()).isEmpty();
        assertThat(run.sent()).isEqualTo(5);
        ArgumentCaptor<OpportunityQueue.Proposal> sent = ArgumentCaptor.forClass(OpportunityQueue.Proposal.class);
        verify(queue, times(5)).propose(sent.capture());
        assertThat(sent.getAllValues()).extracting(OpportunityQueue.Proposal::title)
                .containsExactly("A95", "A90", "B80", "G99", "G98");
        verify(extractor).extract(any(), any(), anyString(),
                argThat(t -> t.contains("<track name=\"alpha\">") && t.contains("<track name=\"beta\">")
                        && t.contains("Beta notes")),
                anyString(), anyInt());
        assertThat(postingRepository.findByCanonicalKey(OpportunityKeys.canonicalKey("https://jobs.example/A85"))).get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(JobPosting.Status.PENDING);
                    assertThat(p.getTrack()).isEqualTo("alpha");
                });
        // A track the user does not have goes to the general list.
        assertThat(postingRepository.findByCanonicalKey(OpportunityKeys.canonicalKey("https://jobs.example/G97"))).get()
                .extracting(JobPosting::getTrack).isNull();
    }

    @Test
    void unreadableTracksLeaveTheMailUnscannedAndSendNothing() {
        when(queue.jobTracks()).thenThrow(new IllegalStateException("No Google credentials"));

        JobCandidateService.RunSummary run = service.run(true, NOW);

        assertThat(run.mailsRead()).isZero();
        assertThat(run.sent()).isZero();
        assertThat(run.errors()).anyMatch(e -> e.contains("job tracks unavailable") && e.contains("No Google credentials"));
        assertThat(scanRepository.count()).isZero();
        verify(queue, times(0)).propose(any());
    }

    @Test
    void firestoreTracksSkipEntriesWithoutNameOrNotesAndDefaultTheLimit() {
        assertThat(FirestoreOpportunityQueue.parseTracks(List.of(
                java.util.Map.of("name", " alpha ", "daily_limit", 2L, "notes", "Alpha notes"),
                java.util.Map.of("name", "beta", "notes", "Beta notes"),
                java.util.Map.of("name", "", "notes", "orphan notes"),
                java.util.Map.of("name", "empty", "daily_limit", 3L),
                "not a map")))
                .containsExactly(
                        new OpportunityQueue.Track("alpha", 2, "Alpha notes"),
                        new OpportunityQueue.Track("beta", 3, "Beta notes"));
        assertThat(FirestoreOpportunityQueue.parseTracks(null)).isEmpty();
    }

    private static JobPostingExtractor.Found found(String title, int score, String track) {
        return new JobPostingExtractor.Found(
                title, "Co " + title, "Zürich", "https://jobs.example/" + title, "summary", score, "reason", track, JobPosting.Region.CH, null);
    }

    private void clean() {
        postingRepository.deleteAll();
        scanRepository.deleteAll();
        emailRepository.deleteAll(emailRepository.findBySourcePathStartingWith("test-tracks/"));
    }
}
