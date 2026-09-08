package com.emailtool.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Correction;
import com.emailtool.domain.Email;
import com.emailtool.persist.CorrectionRepository;
import com.emailtool.persist.EmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorrectionServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    EmailRepository emailRepository;

    @Mock
    CorrectionRepository correctionRepository;

    private CorrectionService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setTrainingDir(tempDir.toString());
        properties.setPromptVersion("3");
        properties.setTaxonomyVersion(1);
        service = new CorrectionService(emailRepository, correctionRepository, properties, new ObjectMapper());
        when(emailRepository.save(any(Email.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(correctionRepository.save(any(Correction.class))).thenAnswer(invocation -> {
            Correction correction = invocation.getArgument(0);
            correction.setId(9L);
            return correction;
        });
    }

    @Test
    void storesUserLabelAndAppendsJsonl() throws Exception {
        Email email = new Email();
        email.setId(4L);
        email.setCategory(Category.NEWSLETTER_OTHER);
        email.setClassificationSource(ClassificationSource.RULE);
        email.setSummary("List-Unsubscribe header present");
        email.setSubject("Claim your $SWEAT");
        email.setFromAddress("wallet@example.test");
        email.setTextPlain("buy coins now");
        email.setPromptVersion("2");
        email.setTaxonomyVersion(1);
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        Correction saved = service.correctCategory(4L, Category.PROMOTION);

        assertEquals(Category.PROMOTION, email.getCategory());
        assertEquals(ClassificationSource.USER, email.getClassificationSource());
        assertEquals(1.0, email.getConfidence());
        assertFalse(email.isNeedsManualReview());
        assertEquals("Claim your $SWEAT", email.getSummary());
        assertEquals("NEWSLETTER_OTHER", saved.getPredicted());
        assertEquals("PROMOTION", saved.getCorrected());
        assertEquals("RULE", saved.getClassificationSource());

        Path jsonl = tempDir.resolve("corrections.jsonl");
        assertTrue(Files.exists(jsonl));
        JsonNode row = new ObjectMapper().readTree(Files.readString(jsonl));
        assertEquals(4, row.get("email_id").asLong());
        assertEquals("NEWSLETTER_OTHER", row.get("predicted").asText());
        assertEquals("PROMOTION", row.get("corrected").asText());
        assertEquals("buy coins now", row.get("text_plain").asText());
        assertEquals("Claim your $SWEAT", row.get("subject").asText());
    }
}
