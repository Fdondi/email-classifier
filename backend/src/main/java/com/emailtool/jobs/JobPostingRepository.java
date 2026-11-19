package com.emailtool.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByCanonicalKey(String canonicalKey);

    List<JobPosting> findByStatus(JobPosting.Status status);

    List<JobPosting> findAllByOrderByIdDesc();

    List<JobPosting> findByStatusAndSentAtAfter(JobPosting.Status status, Instant after);

    List<JobPosting> findByCompanyKeyIsNull();

    /** Indexed lookup: was this company sent after {@code after}? */
    boolean existsByCompanyKeyAndStatusAndSentAtAfter(String companyKey, JobPosting.Status status, Instant after);
}
