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
import java.time.LocalDate;
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

    @Test
    void togglesActionNeededWithoutChangingCategory() throws Exception {
        Email email = new Email();
        email.setId(4L);
        email.setCategory(Category.EVENT);
        email.setActionNeeded(false);
        email.setClassificationSource(ClassificationSource.LLM);
        email.setFromAddress("events@example.test");
        email.setSubject("RSVP please");
        email.setTextPlain("please reply");
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        Correction saved = service.correctActionNeeded(4L, true);

        assertEquals(Category.EVENT, email.getCategory());
        assertTrue(email.isActionNeeded());
        assertEquals(ClassificationSource.USER, email.getClassificationSource());
        assertEquals("action_needed", saved.getField());
        assertEquals("false", saved.getPredicted());
        assertEquals("true", saved.getCorrected());

        Path jsonl = tempDir.resolve("corrections.jsonl");
        JsonNode row = new ObjectMapper().readTree(Files.readString(jsonl));
        assertEquals("action_needed", row.get("field").asText());
        assertEquals("true", row.get("corrected").asText());
    }

    @Test
    void categoryCorrectionLeavesActionNeededUnchanged() {
        Email email = new Email();
        email.setId(4L);
        email.setCategory(Category.EVENT);
        email.setActionNeeded(true);
        email.setClassificationSource(ClassificationSource.LLM);
        email.setFromAddress("events@example.test");
        email.setSubject("Meetup");
        email.setTextPlain("thursday");
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        service.correctCategory(4L, Category.JOB_OFFERS);

        assertEquals(Category.JOB_OFFERS, email.getCategory());
        assertTrue(email.isActionNeeded());
    }

    @Test
    void storesRatingAndAppendsJsonl() throws Exception {
        Email email = new Email();
        email.setId(4L);
        email.setBlendedScore(0.3);
        email.setDueDate(LocalDate.of(2026, 9, 20));
        email.setSubject("Briefing");
        email.setFromAddress("events@example.test");
        email.setTextPlain("tomorrow");
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        Correction saved = service.correctRating(4L, 0.9, 0.8, 0.2, 0.3);

        assertEquals(0.9, email.getRatingUrgency());
        assertEquals(0.8, email.getRatingImportance());
        assertEquals(0.8, email.getBlendedScore());
        assertEquals(80, email.getInterestScore());
        assertEquals("rating", saved.getField());
        JsonNode predicted = new ObjectMapper().readTree(saved.getPredicted());
        JsonNode corrected = new ObjectMapper().readTree(saved.getCorrected());
        assertEquals(0.2, predicted.get("urgency").asDouble());
        assertEquals(0.3, predicted.get("importance").asDouble());
        assertEquals(0.9, corrected.get("urgency").asDouble());
        assertEquals(0.8, corrected.get("importance").asDouble());

        Path jsonl = tempDir.resolve("corrections.jsonl");
        JsonNode row = new ObjectMapper().readTree(Files.readString(jsonl));
        assertEquals("rating", row.get("field").asText());
        assertEquals(0.9, row.get("corrected_urgency").asDouble());
        assertEquals(0.8, row.get("corrected_importance").asDouble());
        assertEquals("Briefing", row.get("subject").asText());
    }

    @Test
    void storesDueDateAndAppendsJsonl() throws Exception {
        Email email = new Email();
        email.setId(4L);
        email.setDueDate(LocalDate.of(2026, 9, 20));
        email.setSubject("Invoice");
        email.setFromAddress("billing@example.test");
        email.setTextPlain("pay soon");
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        Correction saved = service.correctDueDate(4L, LocalDate.of(2026, 9, 22));

        assertEquals(LocalDate.of(2026, 9, 22), email.getDueDate());
        assertEquals("due_date", saved.getField());
        assertEquals("2026-09-20", saved.getPredicted());
        assertEquals("2026-09-22", saved.getCorrected());

        Path jsonl = tempDir.resolve("corrections.jsonl");
        JsonNode row = new ObjectMapper().readTree(Files.readString(jsonl));
        assertEquals("due_date", row.get("field").asText());
        assertEquals("2026-09-22", row.get("corrected").asText());
    }

    @Test
    void storesActionNeededAndAppendsJsonl() throws Exception {
        Email email = new Email();
        email.setId(4L);
        email.setCategory(Category.EVENT);
        email.setActionNeeded(false);
        email.setSubject("RSVP");
        email.setFromAddress("events@example.test");
        email.setTextPlain("please reply");
        when(emailRepository.findById(4L)).thenReturn(Optional.of(email));

        Correction saved = service.correctActionNeeded(4L, true);

        assertTrue(email.isActionNeeded());
        assertEquals("action_needed", saved.getField());
        assertEquals("false", saved.getPredicted());
        assertEquals("true", saved.getCorrected());
    }
}
