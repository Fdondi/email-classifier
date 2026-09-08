package com.emailtool.api;

import com.emailtool.api.dto.CorrectionDto;
import com.emailtool.feedback.CorrectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/corrections")
public class CorrectionController {

    private final CorrectionService correctionService;

    public CorrectionController(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @GetMapping
    public List<CorrectionDto> list() {
        return correctionService.list();
    }
}
