package com.emailtool.jobs;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByCompanyKeyAndAppliedAtAfter(String companyKey, Instant after);
}
