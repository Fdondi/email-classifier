package com.emailtool.normalize;

import com.emailtool.domain.Email;
import com.emailtool.ingest.MimeParser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NormalizeService {

    private final HiddenContentStripper hiddenContentStripper;
    private final QuotedReplyTrimmer quotedReplyTrimmer;
    private final HtmlSanitizerService htmlSanitizerService;
    private final LanguageDetectorService languageDetectorService;

    public NormalizeService(
            HiddenContentStripper hiddenContentStripper,
            QuotedReplyTrimmer quotedReplyTrimmer,
            HtmlSanitizerService htmlSanitizerService,
            LanguageDetectorService languageDetectorService) {
        this.hiddenContentStripper = hiddenContentStripper;
        this.quotedReplyTrimmer = quotedReplyTrimmer;
        this.htmlSanitizerService = htmlSanitizerService;
        this.languageDetectorService = languageDetectorService;
    }

    public Result normalize(MimeParser.ParsedMime parsed) {
        String visibleFromHtml =
                parsed.textHtml == null ? "" : hiddenContentStripper.toVisibleText(parsed.textHtml);
        String plain = parsed.textPlain;
        if (plain == null || plain.isBlank()) {
            plain = visibleFromHtml;
        }
        String trimmed = quotedReplyTrimmer.trim(plain);
        HtmlSanitizerService.SanitizeResult sanitized;
        if (parsed.textHtml != null && !parsed.textHtml.isBlank()) {
            sanitized = htmlSanitizerService.sanitize(parsed.textHtml);
        } else {
            String escaped = htmlSanitizerService.escapePlainToHtml(trimmed);
            sanitized = new HtmlSanitizerService.SanitizeResult(escaped, List.of());
        }
        String language = languageDetectorService.detectIso6391(trimmed);
        return new Result(trimmed, sanitized.html(), sanitized.remoteImageUrls(), language);
    }

    public void apply(Email email, Result result) {
        email.setTextPlain(result.textPlain());
        email.setHtmlSanitized(result.htmlSanitized());
        email.setLanguage(result.language());
    }

    public record Result(String textPlain, String htmlSanitized, List<String> remoteImageUrls, String language) {}
}
