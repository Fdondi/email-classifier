package com.emailtool.jobs;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByCanonicalKey(String canonicalKey);

    List<JobPosting> findByStatus(JobPosting.Status status);

    List<JobPosting> findAllByOrderByIdDesc();
}
