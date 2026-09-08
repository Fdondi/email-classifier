package com.emailtool.api;

import com.emailtool.api.dto.CategoryPageDto;
import com.emailtool.api.dto.DigestDto;
import com.emailtool.api.dto.EmailSummaryDto;
import com.emailtool.domain.Category;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DigestController {

    private final DigestService digestService;

    public DigestController(DigestService digestService) {
        this.digestService = digestService;
    }

    @GetMapping("/digest")
    public DigestDto digest(@RequestParam(defaultValue = "false") boolean includeRead) {
        return digestService.digest(includeRead);
    }

    @GetMapping("/digest/category/{category}")
    public CategoryPageDto category(
            @PathVariable Category category,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "false") boolean includeRead) {
        return digestService.page(category, offset, limit, includeRead);
    }

    @GetMapping("/digest/spam")
    public List<EmailSummaryDto> spam(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean includeRead) {
        return digestService.page(Category.SPAM, offset, limit, includeRead).items();
    }

    @GetMapping("/digest/hidden")
    public List<EmailSummaryDto> hidden(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return digestService.hidden(offset, limit);
    }
}
