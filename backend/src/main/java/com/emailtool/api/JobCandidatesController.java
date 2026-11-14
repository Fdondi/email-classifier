package com.emailtool.api;

import com.emailtool.jobs.JobPosting;
import com.emailtool.jobs.JobPostingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Postings found in job mail and whether they went to letter-writer (read-only). */
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

    private final JobPostingRepository repository;

    public JobCandidatesController(JobPostingRepository repository) {
        this.repository = repository;
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
