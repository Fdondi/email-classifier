package com.emailtool.jobs;

import com.emailtool.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Writes proposals the way letter-writer's {@code OpportunityStore.upsert_proposed} does (same
 * document id, fields and merge rules), so the web app and the phone list them under "To review".
 */
public class FirestoreOpportunityQueue implements OpportunityQueue, AutoCloseable {

    static final String PROPOSED = "proposed";
    static final String VIA = "email-tool";

    private final AppProperties.Firestore config;
    private volatile Firestore db;

    public FirestoreOpportunityQueue(AppProperties.Firestore config) {
        this.config = config;
    }

    @Override
    public Outcome propose(Proposal p) {
        String userId = requireUserId();
        String id = OpportunityKeys.documentId(userId, p.canonicalKey());
        DocumentReference ref = db().collection(config.getCollection()).document(id);
        Timestamp now = Timestamp.now();
        try {
            boolean created = db().runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists()) {
                    tx.set(ref, newDocument(userId, p, now));
                    return true;
                }
                if (!userId.equals(snap.getString("user_id"))) {
                    throw new IllegalStateException("Opportunity " + id + " belongs to a different user");
                }
                Map<String, Object> update = new HashMap<>();
                fillIfEmpty(update, snap, "title", p.title());
                fillIfEmpty(update, snap, "company", p.company());
                fillIfEmpty(update, snap, "location", p.location());
                fillIfEmpty(update, snap, "url", p.url());
                Object ids = snap.get("alert_message_ids");
                if (p.alertMessageId() != null
                        && !(ids instanceof List<?> list && list.contains(p.alertMessageId()))) {
                    update.put("alert_message_ids", FieldValue.arrayUnion(p.alertMessageId()));
                }
                if (!update.isEmpty()) {
                    update.put("updated_at", now);
                    tx.update(ref, update);
                }
                return false;
            }).get(60, TimeUnit.SECONDS);
            return new Outcome(id, created);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Firestore write failed: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Firestore write timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore write interrupted", e);
        }
    }

    static Map<String, Object> newDocument(String userId, Proposal p, Timestamp now) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("user_id", userId);
        doc.put("canonical_key", p.canonicalKey());
        doc.put("source", OpportunityKeys.sourceOf(p.canonicalKey()));
        doc.put("url", p.url());
        doc.put("title", nullToEmpty(p.title()).strip());
        doc.put("company", nullToEmpty(p.company()).strip());
        doc.put("location", nullToEmpty(p.location()).strip());
        doc.put("status", PROPOSED);
        doc.put("skip_reason", null);
        doc.put("alert_message_ids", p.alertMessageId() == null ? List.of() : List.of(p.alertMessageId()));
        if (p.captureText() != null && !p.captureText().isBlank()) {
            Map<String, Object> capture = new LinkedHashMap<>();
            capture.put("text", p.captureText().strip());
            capture.put("html", p.captureHtml());
            capture.put("captured_at", now);
            capture.put("via", VIA);
            doc.put("capture", capture);
        } else {
            doc.put("capture", null);
        }
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("by", VIA);
        proposal.put("interest_score", p.interestScore());
        proposal.put("summary", nullToEmpty(p.summary()));
        proposal.put("reason", nullToEmpty(p.reason()));
        doc.put("proposal", proposal);
        doc.put("created_at", now);
        doc.put("updated_at", now);
        return doc;
    }

    @Override
    public Optional<String> latestCv() {
        String userId = requireUserId();
        try {
            DocumentSnapshot snap = db().collection("personal_data").document(userId).get().get(30, TimeUnit.SECONDS);
            Object revisions = snap.exists() ? snap.get("cv_revisions") : null;
            if (!(revisions instanceof List<?> list)) {
                return Optional.empty();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(r -> (Map<?, ?>) r)
                    .max(Comparator.comparing(r -> String.valueOf(r.get("created_at"))))
                    .map(r -> r.get("content"))
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank());
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not read the CV from Firestore: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public List<Track> jobTracks() {
        String userId = requireUserId();
        try {
            DocumentSnapshot snap = db().collection("personal_data").document(userId).get().get(30, TimeUnit.SECONDS);
            return parseTracks(snap.exists() ? snap.get("job_tracks") : null);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not read the job tracks from Firestore: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted reading the job tracks", e);
        }
    }

    /** {@code job_tracks}: a list of {@code {name, daily_limit, notes}}; entries without a name or notes are skipped. */
    static List<Track> parseTracks(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(m -> (Map<?, ?>) m)
                .map(m -> new Track(
                        Objects.toString(m.get("name"), "").strip(),
                        m.get("daily_limit") instanceof Number n ? Math.max(0, n.intValue()) : 3,
                        Objects.toString(m.get("notes"), "").strip()))
                .filter(t -> !t.name().isEmpty() && !t.notes().isEmpty())
                .toList();
    }

    private static void fillIfEmpty(Map<String, Object> update, DocumentSnapshot snap, String field, String value) {
        if (value != null && !value.isBlank() && nullToEmpty(snap.getString(field)).isBlank()) {
            update.put(field, value.strip());
        }
    }

    private String requireUserId() {
        String userId = config.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("app.jobs.firestore.user-id is not set");
        }
        return userId;
    }

    private Firestore db() {
        Firestore current = db;
        if (current == null) {
            synchronized (this) {
                if (db == null) {
                    db = open();
                }
                current = db;
            }
        }
        return current;
    }

    private Firestore open() {
        if (config.getProjectId() == null || config.getProjectId().isBlank()) {
            throw new IllegalStateException("app.jobs.firestore.project-id is not set");
        }
        try {
            GoogleCredentials credentials;
            String file = config.getCredentialsFile();
            if (file != null && !file.isBlank()) {
                try (InputStream in = new FileInputStream(file)) {
                    credentials = GoogleCredentials.fromStream(in);
                }
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }
            return FirestoreOptions.newBuilder()
                    .setProjectId(config.getProjectId())
                    .setDatabaseId(Objects.requireNonNullElse(config.getDatabaseId(), "(default)"))
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No Google credentials for Firestore (run `gcloud auth application-default login`"
                            + " or set app.jobs.firestore.credentials-file): " + e.getMessage(), e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @Override
    public void close() throws Exception {
        Firestore current = db;
        if (current != null) {
            current.close();
        }
    }
}
