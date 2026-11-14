package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobPostingExtractorTest {

    private final JobPostingExtractor extractor = new JobPostingExtractor(null, new ObjectMapper());
    private final LinkedText body = new LinkedText("x", List.of("https://a.example/1", "https://b.example/2"));

    @Test
    void resolvesMarkersClampsScoresAndDropsPostingsWithoutAKnownLink() {
        String reply = """
                ```json
                {"postings":[
                  {"title":"Embedded Lead","company":"Acme","location":"Zürich","link":"L2","summary":"s","track":" ALPHA ","interest_score":140,"reason":"r"},
                  {"title":"Invented","company":"X","location":"","link":"L9","summary":"","interest_score":90,"reason":""},
                  {"title":"No link","company":"X","location":"","link":null,"summary":"","interest_score":90,"reason":""},
                  {"title":"","company":"X","location":"","link":"L1","summary":"","interest_score":90,"reason":""}
                ]}
                ```""";
        List<JobPostingExtractor.Found> found = extractor.parse(reply, body).orElseThrow();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).url()).isEqualTo("https://b.example/2");
        assertThat(found.get(0).interestScore()).isEqualTo(100);
        assertThat(found.get(0).track()).isEqualTo("alpha");
    }

    @Test
    void emptyListIsAnAnswerButGarbageIsNot() {
        assertThat(extractor.parse("{\"postings\":[]}", body)).contains(List.of());
        assertThat(extractor.parse("I cannot help with that", body)).isEmpty();
    }
}
