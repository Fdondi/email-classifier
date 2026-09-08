package com.emailtool.job;

import com.emailtool.api.dto.TriageRunDto;
import com.emailtool.domain.TriageRun;
import com.emailtool.domain.TriageRunOutcome;
import com.emailtool.persist.EmailRepository;
import com.emailtool.persist.TriageRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriageRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(TriageRunRecorder.class);

    private final TriageRunRepository runRepository;
    private final EmailRepository emailRepository;

    public TriageRunRecorder(TriageRunRepository runRepository, EmailRepository emailRepository) {
        this.runRepository = runRepository;
        this.emailRepository = emailRepository;
    }

    public TriageRunDto record(
            String source,
            Instant started,
            Instant finished,
            boolean llmReady,
            int requeued,
            TriageJobService.TriageJobResult result) {
        int unclassifiedLeft = (int) emailRepository.countByCategoryIsNull();
        TriageRunOutcome outcome = outcome(result);
        TriageRun run = new TriageRun();
        run.setStartedAt(started);
        run.setFinishedAt(finished);
        run.setDurationMs(Math.max(0, Duration.between(started, finished).toMillis()));
        run.setSource(source == null || source.isBlank() ? "ui" : source);
        run.setOutcome(outcome);
        run.setIngested(result.ingested());
        run.setSkipped(result.skipped());
        run.setIngestFailed(result.ingestFailed());
        run.setRequeued(requeued);
        run.setClassifiedByRule(result.classifiedByRule());
        run.setClassifiedByLlm(result.classifiedByLlm());
        run.setFallback(result.fallback());
        run.setUnclassifiedLeft(unclassifiedLeft);
        run.setLlmReady(llmReady);
        run.setErrors(result.errors().isEmpty() ? null : String.join("\n", result.errors()));
        try {
            return toDto(runRepository.save(run));
        } catch (RuntimeException e) {
            log.warn("Could not persist triage run: {}", e.getMessage());
            return toDto(run);
        }
    }

    @Transactional(readOnly = true)
    public List<TriageRunDto> list() {
        return runRepository.findAllByOrderByStartedAtDesc().stream().map(TriageRunRecorder::toDto).toList();
    }

    static TriageRunOutcome outcome(TriageJobService.TriageJobResult result) {
        if (result.errors().isEmpty()) {
            return TriageRunOutcome.OK;
        }
        if (result.classifiedByLlm() > 0 || result.classifiedByRule() > 0 || result.ingested() > 0) {
            return TriageRunOutcome.PARTIAL;
        }
        return TriageRunOutcome.FAILED;
    }

    static TriageRunDto toDto(TriageRun run) {
        long id = run.getId() == null ? 0L : run.getId();
        String raw = run.getErrors();
        List<String> errors = raw == null || raw.isBlank() ? List.of() : Arrays.asList(raw.split("\\n"));
        return new TriageRunDto(
                id,
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getSource(),
                run.getOutcome() == null ? TriageRunOutcome.FAILED.name() : run.getOutcome().name(),
                run.getIngested(),
                run.getSkipped(),
                run.getIngestFailed(),
                run.getRequeued(),
                run.getClassifiedByRule(),
                run.getClassifiedByLlm(),
                run.getFallback(),
                run.getUnclassifiedLeft(),
                run.isLlmReady(),
                errors);
    }
}
