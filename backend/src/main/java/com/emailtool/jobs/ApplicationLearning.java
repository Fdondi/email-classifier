package com.emailtool.jobs;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.jobs.OutcomeMailReader.Event;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.EmailRepository.MailHead;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What past applications teach about new postings. Facts: every application since 2025 in
 * {@code job_application}, seeded once from the Gmail backfill in letter-writer's {@code opportunities},
 * then kept current from outcome mail (sender rules from Firestore {@code application_learning/{user}},
 * the local model otherwise). From them, a Bayesian estimate per company, way of applying and company
 * type: groups more than one standard deviation from the average are told to the model that scores
 * new postings. Whenever outcome mail changes an application, the counts and those notes are written
 * back to the Firestore document.
 */
public class ApplicationLearning {

    static final List<String> STAGES = List.of("applied", "received", "viewed", "forwarded", "screening", "interview", "final", "offer");
    /** Outcome mail older than this is not read (the backfill covers everything before it went live). */
    private static final Duration WINDOW = Duration.ofDays(14);
    private static final Duration GHOSTED_AFTER = Duration.ofDays(30);
    /** Subjects worth a model call outside JOB_INTERVIEWS: rejections and receipts often land in OTHER. */
    private static final Pattern APPLICATION_WORDS =
            Pattern.compile("(?i)applica|applying|bewerbung|absage|candidatur|interview|gespr[aä]ch|assessment");
    /** Known only from these, an application is left out of the rates (as in the backfill). */
    private static final Set<String> SIGNAL_ONLY_KINDS = Set.of("screening", "interview", "final", "offer");
    /** How many applications the average weighs in each group's estimate: five unanswered ones barely move it. */
    static final int PRIOR_WEIGHT = 20;
    /** shortcut: the model is told at most this many groups (largest deviation first); raise it if real groups get cut. */
    private static final int MAX_SIGNALS = 30;

    record Metric(String name, String verb, Predicate<JobApplication> hit) {}

    static final List<Metric> METRICS = List.of(
            new Metric("answer", "got an answer (a rejection counts)",
                    a -> "rejected".equals(a.result) || a.reachedHuman || reached(a, "screening")),
            new Metric("screening", "reached a screening or interview", a -> reached(a, "screening")));

    /** Ways to group applications that a posting can be matched against ("unknown" values are skipped). */
    private static final Map<String, Function<JobApplication, String>> DIMENSIONS = new LinkedHashMap<>();

    static {
        DIMENSIONS.put("company", a -> a.companyKey);
        DIMENSIONS.put("how applied", a -> a.channel);
        DIMENSIONS.put("company type", a -> a.companyType);
        DIMENSIONS.put("how applied × company type", a -> a.channel + " × " + a.companyType);
    }

    /**
     * A group whose posterior rate (Beta prior centred on the average, worth {@link #PRIOR_WEIGHT}
     * applications) lies more than one posterior standard deviation from that average.
     */
    record Signal(String dimension, String value, Metric metric, int n, int hits, double estimate, double average, double z) {
        String key() {
            return dimension + "|" + value + "|" + metric.name() + "|" + (z < 0 ? "below" : "above");
        }

        String text() {
            return String.format(Locale.ROOT, "%s \"%s\": %d of %d %s → estimated %.1f%% vs %.1f%% average (%.1fσ %s)",
                    dimension, value, hits, n, metric.verb(), 100 * estimate, 100 * average, Math.abs(z), z < 0 ? "below" : "above");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ApplicationLearning.class);

    record SenderRule(String sender, String domain, Pattern subject, String kind, String channel) {}

    /** {@code notes}: what the scoring model is told, "" when no group stands out. */
    public record Overview(long applications, String notes, List<JobApplicationMail> changes) {}

    private final AppProperties properties;
    private final OpportunityQueue queue;
    private final JobApplicationRepository applications;
    private final JobApplicationMailRepository mails;
    private final JobPostingRepository postings;
    private final EmailRepository emails;
    private final OutcomeMailReader reader;

    public ApplicationLearning(
            AppProperties properties,
            OpportunityQueue queue,
            JobApplicationRepository applications,
            JobApplicationMailRepository mails,
            JobPostingRepository postings,
            EmailRepository emails,
            OutcomeMailReader reader) {
        this.properties = properties;
        this.queue = queue;
        this.applications = applications;
        this.mails = mails;
        this.postings = postings;
        this.emails = emails;
        this.reader = reader;
    }

    /**
     * Seeds the applications once, reads new outcome mail, rewrites the counts in Firestore if anything
     * changed. Returns the notes for the scoring model ("" when none or the history is unavailable).
     */
    public String refresh(Instant now, boolean llmReady, List<String> errors) {
        Map<String, Object> doc;
        try {
            doc = queue.learning();
            if (applications.count() == 0) {
                // Everything is read before the first row is written, so a network failure leaves the table empty.
                seed(queue.ledgerApplications());
            }
        } catch (RuntimeException e) {
            errors.add("Application learning: " + e.getMessage());
            return "";
        }
        List<JobApplication> all = new ArrayList<>(applications.findAll());
        int changes = readOutcomeMail(now, llmReady, senderRules(doc.get("sender_rules")), all, errors);
        if (changes > 0) {
            try {
                queue.updateLearning(counts(all, now));
            } catch (RuntimeException e) {
                errors.add("Application learning: counts not saved to Firestore: " + e.getMessage());
            }
        }
        return notes(all);
    }

    /** Whether the user applied to this company (by {@link JobCandidateService#companyKey}) after {@code since}. */
    public boolean appliedSince(String companyKey, Instant since) {
        return applications.existsByCompanyKeyAndAppliedAtAfter(companyKey, since);
    }

    public Overview overview() {
        List<JobApplication> all = applications.findAll();
        return new Overview(all.size(), notes(all), mails.findTop30ByChangeIsNotNullOrderByAtDesc());
    }

    static List<Signal> signals(List<JobApplication> all) {
        List<JobApplication> rated = all.stream().filter(a -> !a.signalOnly).toList();
        List<Signal> out = new ArrayList<>();
        for (Metric m : METRICS) {
            double average = average(rated, m);
            DIMENSIONS.forEach((dimension, by) -> {
                Map<String, List<JobApplication>> groups = new TreeMap<>();
                rated.forEach(a -> groups.computeIfAbsent(by.apply(a), k -> new ArrayList<>()).add(a));
                groups.forEach((value, list) -> {
                    if (value.isEmpty() || value.contains("unknown")) {
                        return;
                    }
                    int n = list.size();
                    int hits = count(list, m.hit());
                    double estimate = (PRIOR_WEIGHT * average + hits) / (PRIOR_WEIGHT + n);
                    double z = (estimate - average) / Math.sqrt(estimate * (1 - estimate) / (PRIOR_WEIGHT + n + 1));
                    if (Math.abs(z) > 1) {
                        out.add(new Signal(dimension, value, m, n, hits, estimate, average, z));
                    }
                });
            });
        }
        out.sort(Comparator.comparingDouble((Signal x) -> -Math.abs(x.z())));
        return out.size() > MAX_SIGNALS ? out.subList(0, MAX_SIGNALS) : out;
    }

    /** Not smoothed: a group of everyone must equal it. At 0% or 100% z is 0/0 (NaN), so nothing is flagged. */
    private static double average(List<JobApplication> rated, Metric m) {
        return rated.isEmpty() ? 0 : (double) count(rated, m.hit()) / rated.size();
    }

    static String notes(List<JobApplication> all) {
        List<Signal> signals = signals(all);
        if (signals.isEmpty()) {
            return "";
        }
        List<JobApplication> rated = all.stream().filter(a -> !a.signalOnly).toList();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "Of the person's %d past applications, on average %.1f%% %s and %.1f%% %s. These groups differ from"
                        + " that average by more than one standard deviation (Bayesian estimates that start from the"
                        + " average, weighted as %d applications, so a few applications move a group little):",
                rated.size(), 100 * average(rated, METRICS.get(0)), METRICS.get(0).verb(),
                100 * average(rated, METRICS.get(1)), METRICS.get(1).verb(), PRIOR_WEIGHT));
        signals.forEach(x -> out.append("\n- ").append(x.text()));
        return out.toString();
    }

    void seed(List<Map<String, Object>> docs) {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> d : docs) {
            Map<?, ?> l = (Map<?, ?>) d.get("ledger");
            List<?> events = l.get("events") instanceof List<?> list ? list : List.of();
            JobApplication a = new JobApplication();
            a.ledgerId = str(l.get("ledger_id"));
            a.company = str(d.get("company"));
            a.companyKey = JobCandidateService.companyKey(a.company);
            a.title = str(d.get("title"));
            a.channel = orElse(str(l.get("channel")), "unknown");
            a.companyType = orElse(str(l.get("company_type")), "unknown");
            a.appliedAt = instant(orElse(str(l.get("applied_at")),
                    events.isEmpty() ? "" : str(((Map<?, ?>) events.get(0)).get("at"))));
            String stage = d.get("outcome") instanceof Map<?, ?> o ? str(o.get("stage")) : "";
            a.stage = STAGES.contains(stage) ? stage : "applied";
            a.result = "rejected".equals(l.get("result_inferred")) ? "rejected" : null;
            a.reachedHuman = Boolean.TRUE.equals(l.get("reached_human"));
            a.signalOnly = Boolean.TRUE.equals(l.get("signal_only"));
            a.updatedAt = a.appliedAt;
            applications.save(a);
            List<JobApplicationMail> rows = new ArrayList<>();
            for (Object o : events) {
                if (o instanceof Map<?, ?> e && !str(e.get("mail_id")).isEmpty() && seen.add(str(e.get("mail_id")))) {
                    rows.add(new JobApplicationMail(
                            str(e.get("mail_id")), null, a.id, instant(str(e.get("at"))), str(e.get("kind")), clip(str(e.get("summary"))), null));
                }
            }
            mails.saveAll(rows);
        }
        log.info("Application learning: seeded {} applications from the backfill", docs.size());
    }

    int readOutcomeMail(Instant now, boolean llmReady, List<SenderRule> senders, List<JobApplication> all, List<String> errors) {
        Instant since = now.minus(WINDOW);
        Optional<JobApplicationMail> backfilled = mails.findTopByEmailIdIsNullOrderByAtDesc();
        if (backfilled.isPresent() && backfilled.get().getAt().isAfter(since)) {
            since = backfilled.get().getAt();
        }
        int changes = 0;
        for (MailHead h : emails.findByDateSentAfter(since)) {
            String mailId = h.getGmailMsgId() != null ? Long.toHexString(h.getGmailMsgId()) : "email:" + h.getId();
            if (mails.existsById(mailId)) {
                continue;
            }
            String subject = nz(h.getSubject());
            Optional<Event> event = byRule(senders, nz(h.getFromAddress()).toLowerCase(Locale.ROOT), subject);
            if (event.isEmpty()) {
                if (h.getCategory() != Category.JOB_INTERVIEWS && !APPLICATION_WORDS.matcher(subject).find()) {
                    continue;
                }
                if (!llmReady) {
                    continue; // read next run
                }
                Optional<Email> email = emails.findById(h.getId());
                event = email.flatMap(e -> reader.read(e, properties.getJobs().getMaxBodyChars()));
                if (event.isEmpty()) {
                    errors.add("Application learning: no usable LLM answer for email " + h.getId());
                    continue;
                }
            }
            if (apply(event.get(), mailId, h, now, all)) {
                changes++;
            }
        }
        return changes;
    }

    /** Applies one outcome event to its application (created when none is open); true when that changed something. */
    boolean apply(Event e, String mailId, MailHead h, Instant now, List<JobApplication> all) {
        Instant at = h.getDateSent() != null ? h.getDateSent() : now;
        String company = !e.company().isBlank() ? e.company() : e.agency();
        String key = JobCandidateService.companyKey(company);
        boolean relevant = !key.isEmpty() && !Set.of("not_application", "ignore").contains(e.kind());
        JobApplication a = !relevant || e.kind().equals("applied") ? null : all.stream()
                .filter(x -> x.companyKey.equals(key) && x.result == null)
                .max(Comparator.comparing((JobApplication x) -> x.appliedAt))
                .orElse(null);
        if (!relevant || (a == null && Set.of("other", "withdrawn").contains(e.kind()))) {
            mails.save(new JobApplicationMail(mailId, h.getId(), null, at, e.kind(), e.summary(), null));
            return false;
        }
        Map<String, Signal> before = byKey(signals(all));
        List<String> what = new ArrayList<>();
        if (a == null) {
            a = new JobApplication();
            a.company = company.strip();
            a.companyKey = key;
            a.title = e.title();
            a.channel = e.channel() != null ? e.channel()
                    : nz(h.getFromAddress()).toLowerCase(Locale.ROOT).endsWith("linkedin.com") ? "linkedin_easy_apply"
                    : !e.agency().isBlank() ? "agency" : "direct_ats";
            a.companyType = companyType(key, e.companyType(), all);
            a.appliedAt = at;
            a.stage = "applied";
            a.signalOnly = SIGNAL_ONLY_KINDS.contains(e.kind());
            all.add(a);
            what.add("new application (" + a.channel + ", " + a.companyType + (a.signalOnly ? ", not counted in rates" : "") + ")");
        }
        if (STAGES.indexOf(e.kind()) > STAGES.indexOf(a.stage)) {
            what.add("stage " + a.stage + " → " + e.kind());
            a.stage = e.kind();
        }
        String result = switch (e.kind()) {
            case "rejection" -> "rejected";
            case "offer" -> "offer";
            case "withdrawn" -> "withdrawn";
            default -> null;
        };
        if (result != null && a.result == null) {
            a.result = result;
            what.add(result);
        }
        if (e.human() && !a.reachedHuman) {
            a.reachedHuman = true;
            what.add("first human reply");
        }
        String change = null;
        if (!what.isEmpty()) {
            a.updatedAt = now;
            applications.save(a);
            Map<String, Signal> after = byKey(signals(all));
            List<String> told = new ArrayList<>();
            after.forEach((k, x) -> {
                if (!before.containsKey(k)) {
                    told.add("model now told: " + x.text());
                }
            });
            before.forEach((k, x) -> {
                if (!after.containsKey(k)) {
                    told.add("model no longer told about " + x.dimension() + " \"" + x.value() + "\" (" + x.metric().name() + ")");
                }
            });
            List<JobApplication> there = all.stream().filter(x -> x.companyKey.equals(key) && !x.signalOnly).toList();
            change = a.company + ": " + e.kind() + " — " + String.join(", ", what) + ". There: " + there.size() + " applications, "
                    + count(there, METRICS.get(0).hit()) + " answered, " + count(there, METRICS.get(1).hit()) + " reached screening"
                    + (told.isEmpty() ? "" : "; " + String.join("; ", told)) + ".";
        }
        mails.save(new JobApplicationMail(mailId, h.getId(), a.id, at, e.kind(), e.summary(), change));
        return change != null;
    }

    /** Earlier applications' label first (the user may have corrected it), then a posting's, then this mail's. */
    private String companyType(String key, String fromMail, List<JobApplication> all) {
        return all.stream()
                .filter(a -> a.companyKey.equals(key) && !a.companyType.equals("unknown"))
                .map(a -> a.companyType)
                .reduce((first, last) -> last)
                .or(() -> postings.findFirstByCompanyKeyAndCompanyTypeIsNotNullOrderByIdDesc(key).map(JobPosting::getCompanyType))
                .orElse(fromMail.isBlank() ? "unknown" : fromMail);
    }

    static Optional<Event> byRule(List<SenderRule> rules, String from, String subject) {
        for (SenderRule r : rules) {
            boolean sender = r.sender() != null ? (r.sender().contains("@") ? from.equals(r.sender()) : onDomain(from, r.sender()))
                    : r.domain() != null && onDomain(from, r.domain());
            Matcher m = r.subject() == null ? null : r.subject().matcher(subject);
            if (!sender || (m != null && !m.find())) {
                continue;
            }
            if (r.kind().equals("ignore")) {
                return Optional.of(new Event("ignore", "", "", "", "", false, clip(subject), null));
            }
            String company = group(m, "company");
            // No company in the subject: the model reads the body instead.
            return company.isEmpty() ? Optional.empty()
                    : Optional.of(new Event(r.kind(), company, "", group(m, "title"), "", false, clip(subject), r.channel()));
        }
        return Optional.empty();
    }

    /** {@code sender_rules.items}; Python's {@code (?P<name>} groups become Java's {@code (?<name>}. */
    static List<SenderRule> senderRules(Object raw) {
        Object items = raw instanceof Map<?, ?> m ? m.get("items") : null;
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<SenderRule> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || Boolean.TRUE.equals(m.get("needs_company_from_body"))) {
                continue;
            }
            String subject = str(m.get("subject"));
            try {
                out.add(new SenderRule(
                        nullIfEmpty(str(m.get("sender")).toLowerCase(Locale.ROOT)),
                        nullIfEmpty(str(m.get("sender_domain")).toLowerCase(Locale.ROOT)),
                        subject.isEmpty() ? null : Pattern.compile(subject.replace("(?P<", "(?<"), Pattern.CASE_INSENSITIVE),
                        str(m.get("kind")),
                        nullIfEmpty(str(m.get("channel")))));
            } catch (PatternSyntaxException e) {
                log.warn("Sender rule with a bad subject pattern skipped: {}", subject);
            }
        }
        return out;
    }

    static Map<String, int[]> companies(List<JobApplication> all) {
        Map<String, int[]> out = new HashMap<>();
        for (JobApplication a : all) {
            if (!a.signalOnly) {
                int[] c = out.computeIfAbsent(a.companyKey, k -> new int[2]);
                c[0]++;
                if (a.reachedHuman) {
                    c[1]++;
                }
            }
        }
        return out;
    }

    /** The fields of the learning document email-tool keeps current, in the backfill's shape (Beta(1,1) posteriors). */
    static Map<String, Object> counts(List<JobApplication> all, Instant now) {
        List<JobApplication> rated = all.stream().filter(a -> !a.signalOnly).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("segments.by_channel", segments(rated, a -> a.channel, now));
        out.put("segments.by_channel_x_company_type", segments(rated, a -> a.channel + " × " + a.companyType, now));
        out.put("segments.by_company_type", segments(rated, a -> a.companyType, now));
        out.put("counterparties.items", companies(all).entrySet().stream()
                .filter(c -> c.getValue()[0] >= 3)
                .sorted(Comparator.comparing((Map.Entry<String, int[]> c) -> -c.getValue()[0]).thenComparing(Map.Entry::getKey))
                .map(c -> {
                    int n = c.getValue()[0];
                    int human = c.getValue()[1];
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("company_key", c.getKey());
                    item.put("applications", n);
                    item.put("reached_human", human);
                    item.put("alpha", 1 + human);
                    item.put("beta", 1 + n - human);
                    item.put("p_human", round((1.0 + human) / (2 + n)));
                    return item;
                })
                .toList());
        out.put("prompt_notes", notes(all));
        out.put("counts_updated_at", now.toString());
        return out;
    }

    private static Map<String, Object> segments(List<JobApplication> rated, Function<JobApplication, String> by, Instant now) {
        Map<String, List<JobApplication>> groups = new TreeMap<>();
        rated.forEach(a -> groups.computeIfAbsent(by.apply(a), k -> new ArrayList<>()).add(a));
        Map<String, Object> out = new LinkedHashMap<>();
        groups.forEach((k, list) -> {
            int n = list.size();
            int screening = count(list, a -> reached(a, "screening"));
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("n", n);
            s.put("viewed", count(list, a -> reached(a, "viewed")));
            s.put("human_contact", count(list, a -> a.reachedHuman));
            s.put("screening_plus", screening);
            s.put("interview_plus", count(list, a -> reached(a, "interview")));
            s.put("rejected", count(list, a -> "rejected".equals(a.result)));
            s.put("ghosted", count(list, a -> !a.reachedHuman && a.result == null && !reached(a, "screening")
                    && a.appliedAt.isBefore(now.minus(GHOSTED_AFTER))));
            s.put("alpha", 1 + screening);
            s.put("beta", 1 + n - screening);
            s.put("p_screen", round((1.0 + screening) / (2 + n)));
            out.put(k, s);
        });
        return out;
    }

    private static boolean reached(JobApplication a, String stage) {
        return STAGES.indexOf(a.stage) >= STAGES.indexOf(stage);
    }

    private static int count(List<JobApplication> list, Predicate<JobApplication> p) {
        return (int) list.stream().filter(p).count();
    }

    private static double round(double p) {
        return Math.round(p * 10_000) / 10_000.0;
    }

    private static Map<String, Signal> byKey(List<Signal> signals) {
        Map<String, Signal> out = new LinkedHashMap<>();
        signals.forEach(x -> out.put(x.key(), x));
        return out;
    }

    private static boolean onDomain(String from, String domain) {
        return from.endsWith("@" + domain) || from.endsWith("." + domain);
    }

    private static String group(Matcher m, String name) {
        return m != null && m.pattern().pattern().contains("(?<" + name + ">") ? nz(m.group(name)).strip() : "";
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    private static String clip(String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).strip();
    }

    private static String orElse(String s, String fallback) {
        return s.isEmpty() ? fallback : s;
    }

    private static String nullIfEmpty(String s) {
        return s.isEmpty() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
