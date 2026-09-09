package com.emailtool.api;

import com.emailtool.api.dto.CorrectionRequest;
import com.emailtool.api.dto.DueDateRequest;
import com.emailtool.api.dto.EmailDetailDto;
import com.emailtool.api.dto.RatingRequest;
import com.emailtool.domain.Email;
import com.emailtool.domain.EmailRemoteResource;
import com.emailtool.feedback.CorrectionService;
import com.emailtool.persist.EmailRepository;
import com.emailtool.security.BodyHtmlService;
import com.emailtool.security.RemoteImageService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    private final DigestService digestService;
    private final EmailRepository emailRepository;
    private final BodyHtmlService bodyHtmlService;
    private final RemoteImageService remoteImageService;
    private final CorrectionService correctionService;

    public EmailController(
            DigestService digestService,
            EmailRepository emailRepository,
            BodyHtmlService bodyHtmlService,
            RemoteImageService remoteImageService,
            CorrectionService correctionService) {
        this.digestService = digestService;
        this.emailRepository = emailRepository;
        this.bodyHtmlService = bodyHtmlService;
        this.remoteImageService = remoteImageService;
        this.correctionService = correctionService;
    }

    @GetMapping("/{id}")
    public EmailDetailDto detail(@PathVariable long id) {
        return digestService.detail(id);
    }

    @GetMapping(value = "/{id}/body", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> body(@PathVariable long id) {
        Email email = emailRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        String html = bodyHtmlService.render(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .header("Content-Security-Policy", "default-src 'none'; img-src data:; style-src 'none'; script-src 'none'")
                .body(html);
    }

    @PostMapping("/{id}/corrections")
    public EmailDetailDto correct(@PathVariable long id, @Valid @RequestBody CorrectionRequest request) {
        correctionService.apply(id, request.category(), request.actionNeeded());
        return digestService.detail(id);
    }

    @PostMapping("/{id}/rating")
    public EmailDetailDto rate(@PathVariable long id, @Valid @RequestBody RatingRequest request) {
        correctionService.correctRating(
                id, request.urgency(), request.importance(), request.predictedUrgency(), request.predictedImportance());
        return digestService.detail(id);
    }

    @PostMapping("/{id}/due-date")
    public EmailDetailDto dueDate(@PathVariable long id, @RequestBody DueDateRequest request) {
        correctionService.correctDueDate(id, request.dueDate());
        return digestService.detail(id);
    }

    @PostMapping("/{id}/hide")
    public Map<String, Boolean> hide(@PathVariable long id) {
        digestService.setUserHidden(id, true);
        return Map.of("hidden", true);
    }

    @PostMapping("/{id}/unhide")
    public Map<String, Boolean> unhide(@PathVariable long id) {
        digestService.setUserHidden(id, false);
        return Map.of("hidden", false);
    }

    @PostMapping("/{id}/load-images")
    public Map<String, Integer> loadImages(@PathVariable long id) {
        int loaded = remoteImageService.loadImages(id);
        return Map.of("loaded", loaded);
    }

    @GetMapping("/{id}/images/{index}")
    public ResponseEntity<byte[]> image(@PathVariable long id, @PathVariable int index) {
        EmailRemoteResource resource = remoteImageService.requireResource(id, index);
        if (resource.getCachedBytes() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not loaded");
        }
        String type = resource.getContentType() == null ? "image/png" : resource.getContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type.split(";")[0].trim()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("image").toString())
                .body(resource.getCachedBytes());
    }
}
