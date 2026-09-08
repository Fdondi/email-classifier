package com.emailtool.normalize;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.springframework.stereotype.Component;

@Component
public class LanguageDetectorService {

    private final LanguageDetector detector = LanguageDetectorBuilder.fromAllLanguages().build();

    public String detectIso6391(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Language language = detector.detectLanguageOf(text);
        if (language == Language.UNKNOWN) {
            return null;
        }
        return language.getIsoCode639_1().name().toLowerCase();
    }
}
