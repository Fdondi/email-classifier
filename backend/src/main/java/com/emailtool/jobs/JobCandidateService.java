package com.emailtool.jobs;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.ingest.MimeParser;
import com.emailtool.persist.EmailRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After triage: reads new JOB_OFFERS mail for individual postings (one local-LLM call per mail,
 * scored against the CV and {@code config/job-profile.md}, tagged with one of the user's career
 * tracks from {@code personal_data.job_tracks}), then proposes the best few to letter-writer (at most
 * {@code dailyLimit} per rolling 24 hours, each track first up to its own quota).
 */
public class JobCandidateService {

    private static final Logger log = LoggerFactory.getLogger(JobCandidateService.class);

    public record RunSummary(int mailsRead, int postingsFound, int sent, int alreadyInApp, List<String> errors) {
        static RunSummary disabled() {
            return new RunSummary(0, 0, 0, 0, List.of());
        }
    }

    private final AppProperties properties;
    private final EmailRepository emailRepository;
    private final JobPostingRepository postingRepository;
    private final JobMailScanRepository scanRepository;
    private final MimeParser mimeParser;
    private final JobPostingExtractor extractor;
    private final OpportunityQueue queue;
    private final PostingPageFetcher fetcher;

    public JobCandidateService(
            AppProperties properties,
            EmailRepository emailRepository,
            JobPostingRepository postingRepository,
            JobMailScanRepository scanRepository,
            MimeParser mimeParser,
            JobPostingExtractor extractor,
            OpportunityQueue queue,
            PostingPageFetcher fetcher) {
        this.properties = properties;
        this.emailRepository = emailRepository;
        this.postingRepository = postingRepository;
        this.scanRepository = scanRepository;
        this.mimeParser = mimeParser;
        this.extractor = extractor;
        this.queue = queue;
        this.fetcher = fetcher;
    }

    public boolean isEnabled() {
        return properties.getJobs().isEnabled();
    }

    public RunSummary run(boolean llmReady, Instant now) {
        if (!isEnabled()) {
            return RunSummary.disabled();
        }
        List<String> errors = new ArrayList<>();
        List<OpportunityQueue.Track> tracks;
        try {
            tracks = queue.jobTracks();
        } catch (RuntimeException e) {
            // Without them, track postings would be rated and sent as general ones: leave everything for the next run.
            errors.add("Job candidates: job tracks unavailable, nothing read or sent: " + e.getMessage());
            return new RunSummary(0, 0, 0, 0, List.copyOf(errors));
        }
        int[] read = {0, 0};
        if (llmReady) {
            read = readNewMail(now, tracks, errors);
        }
        int[] sent = publish(now, tracks, errors);
        log.info("Job candidates: read {} mails, found {} postings, sent {}, already in letter-writer {}",
                read[0], read[1], sent[0], sent[1]);
        return new RunSummary(read[0], read[1], sent[0], sent[1], List.copyOf(errors));
    }

    /** Returns {mails read, postings found}. */
    int[] readNewMail(Instant now, List<OpportunityQueue.Track> tracks, List<String> errors) {
        AppProperties.Jobs cfg = properties.getJobs();
        Instant since = now.minus(Duration.ofHours(cfg.getLookbackHours()));
        List<Email> mails = new ArrayList<>();
        for (Email email : emailRepository.findByCategoryIn(List.of(Category.JOB_OFFERS))) {
            Instant at = email.getDateSent() != null ? email.getDateSent() : email.getIngestedAt();
            if (at != null && !at.isBefore(since) && !scanRepository.existsById(email.getId())) {
                mails.add(email);
            }
        }
        if (mails.isEmpty()) {
            return new int[] {0, 0};
        }
        int errorsBefore = errors.size();
        String profile = profile(errors);
        String trackNotes = trackNotes(tracks);
        if (profile.isBlank() || errors.size() > errorsBefore) {
            // A scanned mail is never rated again, so rating without the CV would fix near-zero scores for good.
            if (errors.size() == errorsBefore) {
                errors.add("Job candidates: no CV in letter-writer and no " + cfg.getProfileFile()
                        + " in " + properties.getConfigDir() + "; job mail left unscanned");
            }
            return new int[] {0, 0};
        }
        int read = 0;
        int found = 0;
        for (Email email : mails) {
            LinkedText body = linkedText(email);
            Optional<List<JobPostingExtractor.Found>> result =
                    extractor.extract(email, body, profile, trackNotes, cfg.getMaxBodyChars());
            if (result.isEmpty()) {
                // Left unscanned: the next run tries this mail again.
                errors.add("Job postings: no usable LLM answer for email " + email.getId());
                continue;
            }
            for (JobPostingExtractor.Found f : result.get()) {
                remember(email, f, knownTrack(tracks, f.track()), now);
            }
            scanRepository.save(new JobMailScan(email.getId(), now, result.get().size(), null));
            read++;
            found += result.get().size();
        }
        return new int[] {read, found};
    }

    private void remember(Email email, JobPostingExtractor.Found f, String track, Instant now) {
        String key = OpportunityKeys.canonicalKey(f.url());
        Optional<JobPosting> existing = postingRepository.findByCanonicalKey(key);
        if (existing.isPresent()) {
            JobPosting p = existing.get();
            // Seen in an earlier alert: keep the better rating, and let it compete again today.
            if (f.interestScore() > p.getInterestScore()) {
                p.setInterestScore(f.interestScore());
                p.setSummary(f.summary());
                p.setReason(f.reason());
                p.setTrack(track);
            }
            if (p.getStatus() == JobPosting.Status.PENDING) {
                p.setFoundAt(now);
            }
            postingRepository.save(p);
            return;
        }
        JobPosting p = new JobPosting();
        p.setEmailId(email.getId());
        p.setCanonicalKey(key);
        p.setUrl(OpportunityKeys.storedUrl(f.url()));
        p.setLinkUrl(f.url());
        p.setTitle(f.title());
        p.setCompany(f.company());
        p.setLocation(f.location());
        p.setSummary(f.summary());
        p.setReason(f.reason());
        p.setInterestScore(f.interestScore());
        p.setTrack(track);
        p.setFoundAt(now);
        postingRepository.save(p);
    }

    /** The user's track the model named, or null (general list) for "" or a name the user has no track for. */
    private static String knownTrack(List<OpportunityQueue.Track> tracks, String name) {
        return tracks.stream()
                .map(OpportunityQueue.Track::name)
                .filter(t -> t.equalsIgnoreCase(name == null ? "" : name.strip()))
                .findFirst()
                .orElse(null);
    }

    /** Returns {sent, already in letter-writer}. */
    int[] publish(Instant now, List<OpportunityQueue.Track> tracks, List<String> errors) {
        AppProperties.Jobs cfg = properties.getJobs();
        Instant dayAgo = now.minus(Duration.ofHours(24));
        List<JobPosting> sentToday = postingRepository.findByStatus(JobPosting.Status.SENT).stream()
                .filter(p -> p.getSentAt() != null && p.getSentAt().isAfter(dayAgo))
                .toList();
        int remaining = Math.max(0, cfg.getDailyLimit() - sentToday.size());
        if (remaining == 0) {
            return new int[] {0, 0};
        }
        Map<String, Integer> trackLeft = new HashMap<>();
        for (OpportunityQueue.Track t : tracks) {
            long used = sentToday.stream().filter(p -> t.name().equals(p.getTrack())).count();
            trackLeft.put(t.name(), (int) Math.max(0, t.dailyLimit() - used));
        }
        Instant freshSince = now.minus(Duration.ofHours(cfg.getLookbackHours()));
        List<JobPosting> candidates = postingRepository.findByStatus(JobPosting.Status.PENDING).stream()
                .filter(p -> p.getInterestScore() >= cfg.getMinInterest())
                .filter(p -> p.getFoundAt() != null && !p.getFoundAt().isBefore(freshSince))
                .sorted(Comparator.comparingInt(JobPosting::getInterestScore).reversed()
                        .thenComparing(JobPosting::getFoundAt, Comparator.reverseOrder()))
                .toList();
        // Track postings first, each track up to its own quota; then the general list fills the rest.
        List<JobPosting> ordered = new ArrayList<>(candidates.stream().filter(p -> trackLeft.containsKey(p.getTrack())).toList());
        ordered.addAll(candidates.stream().filter(p -> !trackLeft.containsKey(p.getTrack())).toList());
        int sent = 0;
        int already = 0;
        for (JobPosting p : ordered) {
            if (remaining == 0) {
                break;
            }
            if (trackLeft.getOrDefault(p.getTrack(), 1) == 0) {
                continue;
            }
            try {
                OpportunityQueue.Outcome outcome = queue.propose(proposal(p));
                p.setOpportunityId(outcome.opportunityId());
                p.setError(null);
                if (outcome.created()) {
                    p.setStatus(JobPosting.Status.SENT);
                    p.setSentAt(now);
                    sent++;
                    remaining--;
                    trackLeft.computeIfPresent(p.getTrack(), (t, left) -> left - 1);
                } else {
                    p.setStatus(JobPosting.Status.ALREADY_IN_APP);
                    already++;
                }
                postingRepository.save(p);
            } catch (RuntimeException e) {
                // Stays PENDING: credentials or network problems hit every posting alike.
                p.setError(e.getMessage());
                postingRepository.save(p);
                errors.add("Job candidates: " + e.getMessage());
                log.warn("Could not send job candidate {}: {}", p.getCanonicalKey(), e.getMessage());
                break;
            }
        }
        return new int[] {sent, already};
    }

    private OpportunityQueue.Proposal proposal(JobPosting p) {
        String text = null;
        String html = null;
        if (!properties.getJobs().isFetchPostings()) {
            p.setCapture("link only: page download disabled");
        } else {
            PostingPageFetcher.Result page = fetcher.fetch(p.getLinkUrl());
            if (page.page() != null) {
                text = page.page().text();
                html = page.page().html();
                p.setCapture("page text");
                rekeyToFinalUrl(p, page.page().finalUrl());
            } else {
                p.setCapture("link only: " + page.linkOnlyReason());
            }
        }
        String alertId = null;
        Optional<Email> email = emailRepository.findById(p.getEmailId());
        if (email.isPresent() && email.get().getGmailMsgId() != null) {
            alertId = Long.toHexString(email.get().getGmailMsgId());
        }
        return new OpportunityQueue.Proposal(
                p.getCanonicalKey(), p.getUrl(), p.getTitle(), p.getCompany(), p.getLocation(), alertId,
                p.getInterestScore(), p.getSummary(), p.getReason(), text, html);
    }

    /** A tracking redirect landed on the real posting: key it like the extension would. */
    private void rekeyToFinalUrl(JobPosting p, String finalUrl) {
        if (finalUrl == null || finalUrl.equals(p.getLinkUrl())) {
            return;
        }
        String key = OpportunityKeys.canonicalKey(finalUrl);
        if (key.equals(p.getCanonicalKey())) {
            return;
        }
        Optional<JobPosting> clash = postingRepository.findByCanonicalKey(key);
        if (clash.isEmpty() || clash.get().getId().equals(p.getId())) {
            p.setCanonicalKey(key);
            p.setUrl(OpportunityKeys.storedUrl(finalUrl));
        }
    }

    LinkedText linkedText(Email email) {
        if (email.getRawMime() != null) {
            try {
                MimeParser.ParsedMime parsed = mimeParser.parse(email.getRawMime());
                return LinkedText.of(parsed.textPlain, parsed.textHtml);
            } catch (Exception e) {
                log.debug("Raw MIME of email {} unreadable, using stored text: {}", email.getId(), e.getMessage());
            }
        }
        return LinkedText.of(email.getTextPlain(), null);
    }

    /** {@code config/job-profile.md} (preferences) plus the newest CV from letter-writer. */
    String profile(List<String> errors) {
        AppProperties.Jobs cfg = properties.getJobs();
        StringBuilder out = new StringBuilder();
        Path file = Path.of(properties.getConfigDir()).resolve(cfg.getProfileFile());
        if (Files.isRegularFile(file)) {
            try {
                out.append("Preferences:\n").append(Files.readString(file, StandardCharsets.UTF_8).strip()).append("\n\n");
            } catch (IOException e) {
                errors.add("Job candidates: cannot read " + file + ": " + e.getMessage());
            }
        }
        try {
            queue.latestCv().ifPresent(cv -> out.append("CV:\n").append(cv.strip()));
        } catch (RuntimeException e) {
            errors.add("Job candidates: CV unavailable, job mail left unscanned: " + e.getMessage());
        }
        String profile = out.toString().strip();
        if (profile.isEmpty()) {
            log.warn("No job profile: add {} or a CV in letter-writer", file);
        }
        return profile.length() > cfg.getMaxProfileChars() ? profile.substring(0, cfg.getMaxProfileChars()) : profile;
    }

    /** Each track's notes as {@code <track name="...">}, for the model. */
    static String trackNotes(List<OpportunityQueue.Track> tracks) {
        StringBuilder out = new StringBuilder();
        for (OpportunityQueue.Track t : tracks) {
            out.append("<track name=\"").append(t.name()).append("\">\n").append(t.notes()).append("\n</track>\n");
        }
        return out.toString().strip();
    }
}
