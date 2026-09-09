package com.emailtool.classify;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClassifierJsonParser {

    private static final Logger log = LoggerFactory.getLogger(ClassifierJsonParser.class);

    private final ObjectMapper mapper;
    private final AppProperties properties;

    public ClassifierJsonParser(ObjectMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public Optional<ClassifierJson> parse(String content) {
        if (content == null || content.isBlank()) {
            log.warn("Classifier JSON rejected: empty model output");
            return Optional.empty();
        }
        String json = extractJsonObject(content);
        try {
            ClassifierJson parsed = mapper.readValue(json, ClassifierJson.class);
            if (parsed.category() == null) {
                log.warn("Classifier JSON rejected: missing category in {}", abbreviate(json));
                return Optional.empty();
            }
            double confidence = parsed.confidence() == null ? 0.0 : clamp(parsed.confidence(), 0.0, 1.0);
            int interest = parsed.interestScore() == null ? 50 : (int) Math.round(clamp(parsed.interestScore(), 0, 100));
            String summary = truncate(parsed.summary() == null ? "" : parsed.summary(), properties.getSummaryMaxChars());
            boolean injection = Boolean.TRUE.equals(parsed.injectionSuspected());
            boolean actionNeeded = parsed.actionNeeded() != null
                    ? parsed.actionNeeded()
                    : parsed.category().isAction();
            String language = parsed.language() == null ? null : parsed.language().toLowerCase(Locale.ROOT);
            if (language != null && language.length() > 8) {
                language = language.substring(0, 8);
            }
            return Optional.of(new ClassifierJson(
                    parsed.category(),
                    confidence,
                    summary,
                    interest,
                    parsed.dueDate(),
                    injection,
                    language,
                    actionNeeded));
        } catch (Exception e) {
            log.warn("Classifier JSON rejected: {} in {}", e.getMessage(), abbreviate(json));
            return Optional.empty();
        }
    }

    public ClassifierJson fallback() {
        return new ClassifierJson(Category.OTHER, 0.0, "", 0, null, false, null, false);
    }

    public static String extractJsonObject(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String abbreviate(String value) {
        String oneLine = value.replace('\n', ' ');
        if (oneLine.length() <= 400) {
            return oneLine;
        }
        return oneLine.substring(0, 400) + "…";
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
