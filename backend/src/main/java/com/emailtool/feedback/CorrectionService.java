package com.emailtool.feedback;

import com.emailtool.api.EmailMapper;
import com.emailtool.api.dto.CorrectionDto;
import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Correction;
import com.emailtool.domain.Email;
import com.emailtool.persist.CorrectionRepository;
import com.emailtool.persist.EmailRepository;
import com.emailtool.rank.RatingScores;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CorrectionService {

    public static final String LIST_UNSUBSCRIBE_SUMMARY = "List-Unsubscribe header present";

    private final EmailRepository emailRepository;
    private final CorrectionRepository correctionRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public CorrectionService(
            EmailRepository emailRepository,
            CorrectionRepository correctionRepository,
            AppProperties properties,
            ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.correctionRepository = correctionRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Correction correctCategory(long emailId, Category corrected) {
        return apply(emailId, corrected, null);
    }

    @Transactional
    public Correction correctActionNeeded(long emailId, boolean actionNeeded) {
        return apply(emailId, null, actionNeeded);
    }

    @Transactional
    public Correction apply(long emailId, Category correctedCategory, Boolean actionNeeded) {
        Email email = emailRepository
                .findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        boolean categoryChange = correctedCategory != null && correctedCategory != email.getCategory();
        boolean actionChange = actionNeeded != null && actionNeeded != email.isActionNeeded();
        if (!categoryChange && !actionChange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to correct");
        }
        Instant now = Instant.now();
        Correction last = null;
        if (categoryChange) {
            last = saveCorrection(
                    email,
                    "category",
                    email.getCategory() == null ? "" : email.getCategory().name(),
                    correctedCategory.name(),
                    now);
            email.setCategory(correctedCategory);
        }
        if (actionChange) {
            last = saveCorrection(
                    email,
                    "action_needed",
                    Boolean.toString(email.isActionNeeded()),
                    Boolean.toString(actionNeeded),
                    now);
            email.setActionNeeded(actionNeeded);
        }
        email.setClassificationSource(ClassificationSource.USER);
        email.setNeedsManualReview(false);
        email.setConfidence(1.0);
        email.setClassifiedAt(now);
        if (LIST_UNSUBSCRIBE_SUMMARY.equals(email.getSummary())) {
            email.setSummary(EmailMapper.cap(email.getSubject(), properties.getSummaryMaxChars()));
        }
        emailRepository.save(email);
        return last;
    }

    @Transactional
    public Correction correctRating(
            long emailId, double urgency, double importance, Double predictedUrgency, Double predictedImportance) {
        Email email = emailRepository
                .findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        double nextUrgency = RatingScores.clamp01(urgency);
        double nextImportance = RatingScores.clamp01(importance);
        LocalDate today = LocalDate.now();
        double previousUrgency =
                predictedUrgency != null ? RatingScores.clamp01(predictedUrgency) : RatingScores.urgency(email, today);
        double previousImportance =
                predictedImportance != null
                        ? RatingScores.clamp01(predictedImportance)
                        : RatingScores.importance(email);
        Instant now = Instant.now();
        Correction saved = saveCorrection(
                email, "rating", ratingJson(previousUrgency, previousImportance), ratingJson(nextUrgency, nextImportance), now);
        email.setRatingUrgency(nextUrgency);
        email.setRatingImportance(nextImportance);
        email.setBlendedScore(nextImportance);
        email.setInterestScore((int) Math.round(nextImportance * 100));
        emailRepository.save(email);
        return saved;
    }

    @Transactional
    public Correction correctDueDate(long emailId, LocalDate dueDate) {
        Email email = emailRepository
                .findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        LocalDate previous = email.getDueDate();
        if (Objects.equals(previous, dueDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date is already set");
        }
        Instant now = Instant.now();
        Correction saved = saveCorrection(
                email,
                "due_date",
                previous == null ? "" : previous.toString(),
                dueDate == null ? "" : dueDate.toString(),
                now);
        email.setDueDate(dueDate);
        emailRepository.save(email);
        return saved;
    }

    private Correction saveCorrection(Email email, String field, String predicted, String corrected, Instant now) {
        Correction correction = new Correction();
        correction.setEmail(email);
        correction.setField(field);
        correction.setPredicted(predicted);
        correction.setCorrected(corrected);
        correction.setModelVersion(nullToEmpty(email.getModelVersion()));
        correction.setPromptVersion(
                email.getPromptVersion() == null || email.getPromptVersion().isBlank()
                        ? properties.getPromptVersion()
                        : email.getPromptVersion());
        correction.setTaxonomyVersion(
                email.getTaxonomyVersion() == null ? properties.getTaxonomyVersion() : email.getTaxonomyVersion());
        correction.setClassificationSource(
                email.getClassificationSource() == null ? "" : email.getClassificationSource().name());
        correction.setCreatedAt(now);
        Correction saved = correctionRepository.save(correction);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    appendTrainingExample(email, saved);
                }
            });
        } else {
            appendTrainingExample(email, saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CorrectionDto> list() {
        return correctionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CorrectionService::toDto)
                .toList();
    }

    private void appendTrainingExample(Email email, Correction correction) {
        try {
            Path dir = Path.of(properties.getTrainingDir());
            Files.createDirectories(dir);
            Path file = dir.resolve("corrections.jsonl");
            String body = email.getTextPlain() == null ? "" : email.getTextPlain();
            int max = properties.getBodyTruncateChars();
            if (body.length() > max) {
                body = body.substring(0, max);
            }
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("email_id", email.getId());
            example.put("field", correction.getField());
            example.put("predicted", correction.getPredicted());
            example.put("corrected", correction.getCorrected());
            example.put("model_version", correction.getModelVersion());
            example.put("prompt_version", correction.getPromptVersion());
            example.put("taxonomy_version", correction.getTaxonomyVersion());
            example.put("classification_source", correction.getClassificationSource());
            example.put("from_address", email.getFromAddress());
            example.put("subject", email.getSubject());
            example.put("text_plain", body);
            example.put("created_at", correction.getCreatedAt().toString());
            if ("rating".equals(correction.getField())) {
                addRatingNumbers(example, correction.getPredicted(), "predicted");
                addRatingNumbers(example, correction.getCorrected(), "corrected");
            }
            Files.writeString(
                    file,
                    objectMapper.writeValueAsString(example) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Correction saved in the database but training JSONL could not be written: "
                    + e.getMessage(), e);
        }
    }

    private void addRatingNumbers(Map<String, Object> example, String json, String prefix) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        example.put(prefix + "_urgency", node.path("urgency").asDouble());
        example.put(prefix + "_importance", node.path("importance").asDouble());
    }

    private String ratingJson(double urgency, double importance) {
        Map<String, Double> value = new LinkedHashMap<>();
        value.put("urgency", urgency);
        value.put("importance", importance);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize rating correction", e);
        }
    }

    private static CorrectionDto toDto(Correction correction) {
        return new CorrectionDto(
                correction.getId(),
                correction.getEmail().getId(),
                correction.getField(),
                correction.getPredicted(),
                correction.getCorrected(),
                correction.getModelVersion(),
                correction.getPromptVersion(),
                correction.getTaxonomyVersion(),
                correction.getClassificationSource(),
                correction.getCreatedAt());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
