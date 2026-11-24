package com.emailtool.jobs;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationMailRepository extends JpaRepository<JobApplicationMail, String> {

    /** What live mail changed in the learning data, newest first. */
    List<JobApplicationMail> findTop30ByChangeIsNotNullOrderByAtDesc();

    /** The newest backfilled event: live mail is read from here on. */
    Optional<JobApplicationMail> findTopByEmailIdIsNullOrderByAtDesc();
}
