package com.emailtool.jobs;

import java.util.List;
import java.util.Optional;

/** letter-writer's side: the {@code opportunities} collection and the user's CV. */
public interface OpportunityQueue {

    /** A job proposed from mail. {@code captureText} is null when only the link could be sent. */
    record Proposal(
            String canonicalKey,
            String url,
            String title,
            String company,
            String location,
            String alertMessageId,
            int interestScore,
            String summary,
            String reason,
            String captureText,
            String captureHtml) {}

    /** {@code created} false: the job already existed (its status is left alone). */
    record Outcome(String opportunityId, boolean created) {}

    /**
     * Creates the job as {@code proposed} (the app's "To review" list), or merges into the existing
     * one with the same key: fills empty fields and adds the mail id, never changes its status.
     */
    Outcome propose(Proposal proposal);

    /** Newest CV text from {@code personal_data}, as letter-writer's batch picks it. */
    Optional<String> latestCv();

    /** A career track: postings the model tags with {@code name}, rated against {@code notes}, at most {@code dailyLimit} per 24h. */
    record Track(String name, int dailyLimit, String notes) {}

    /** The user's career tracks ({@code personal_data.job_tracks}); empty when none are set. */
    List<Track> jobTracks();
}
