package com.emailtool.api;

import com.emailtool.jobs.ApplicationLearning;
import com.emailtool.jobs.JobPosting;
import com.emailtool.jobs.JobPostingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Postings found in job mail, whether they went to letter-writer, and what application outcomes changed (read-only). */
@RestController
@RequestMapping("/api")
public class JobCandidatesController {

    public record JobCandidateDto(
            long id,
            long emailId,
            String title,
            String company,
            String location,
            String url,
            int interestScore,
            String track,
            String summary,
            String reason,
            String status,
            Instant foundAt,
            Instant sentAt,
            String opportunityId,
            String capture,
            String error) {}

    /** A mail that changed what past applications teach (the UI's "Application learning" box). */
    public record LearningChangeDto(Instant at, Long emailId, String kind, String summary, String change) {}

    /** {@code notes}: what the model that rates postings is told about past applications. */
    public record LearningDto(long applications, String notes, List<LearningChangeDto> changes) {}

    private final JobPostingRepository repository;
    private final ApplicationLearning learning;

    public JobCandidatesController(JobPostingRepository repository, ApplicationLearning learning) {
        this.repository = repository;
        this.learning = learning;
    }

    @GetMapping("/application-learning")
    public LearningDto learning() {
        ApplicationLearning.Overview o = learning.overview();
        return new LearningDto(o.applications(), o.notes(), o.changes().stream()
                .map(m -> new LearningChangeDto(m.getAt(), m.getEmailId(), m.getKind(), m.getSummary(), m.getChange()))
                .toList());
    }

    @GetMapping("/job-candidates")
    public List<JobCandidateDto> list(@RequestParam(defaultValue = "100") int limit) {
        return repository.findAllByOrderByIdDesc().stream()
                .limit(Math.max(1, Math.min(limit, 1000)))
                .map(JobCandidatesController::toDto)
                .toList();
    }

    private static JobCandidateDto toDto(JobPosting p) {
        return new JobCandidateDto(
                p.getId(), p.getEmailId(), p.getTitle(), p.getCompany(), p.getLocation(), p.getUrl(),
                p.getInterestScore(), p.getTrack(), p.getSummary(), p.getReason(), p.getStatus().name(), p.getFoundAt(),
                p.getSentAt(), p.getOpportunityId(), p.getCapture(), p.getError());
    }
}
