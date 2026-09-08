package com.emailtool.api;

import com.emailtool.api.dto.TriageRunDto;
import com.emailtool.job.TriageJobService;
import com.emailtool.job.TriageRunRecorder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final TriageJobService triageJobService;
    private final TriageRunRecorder triageRunRecorder;

    public JobController(TriageJobService triageJobService, TriageRunRecorder triageRunRecorder) {
        this.triageJobService = triageJobService;
        this.triageRunRecorder = triageRunRecorder;
    }

    @PostMapping("/triage")
    public TriageJobService.TriageJobResult triage(@RequestParam(defaultValue = "ui") String source) {
        return triageJobService.run(source);
    }

    @GetMapping("/runs")
    public List<TriageRunDto> runs() {
        return triageRunRecorder.list();
    }
}
