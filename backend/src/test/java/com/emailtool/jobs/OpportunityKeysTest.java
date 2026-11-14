package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Same fixture as letter-writer's Python and Kotlin tests: document ids must match across all three. */
class OpportunityKeysTest {

    private static JsonNode fixture() throws Exception {
        try (InputStream in = OpportunityKeysTest.class.getResourceAsStream("/jobs/opportunity_keys.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void canonicalKeysCleanUrlsAndSourcesMatchLetterWriter() throws Exception {
        JsonNode cases = fixture().get("canonical_keys");
        assertThat(cases.size()).isGreaterThan(5);
        for (JsonNode c : cases) {
            String url = c.get("url").asText();
            String key = OpportunityKeys.canonicalKey(url);
            assertThat(key).as("key of %s", url).isEqualTo(c.get("canonical_key").asText());
            if (c.has("clean_url")) {
                assertThat(OpportunityKeys.cleanUrl(url)).as("clean url of %s", url).isEqualTo(c.get("clean_url").asText());
            }
            if (c.has("source")) {
                assertThat(OpportunityKeys.sourceOf(key)).as("source of %s", url).isEqualTo(c.get("source").asText());
            }
        }
    }

    @Test
    void documentIdsMatchLetterWriter() throws Exception {
        JsonNode root = fixture();
        String userId = root.get("user_id").asText();
        for (JsonNode c : root.get("document_ids")) {
            assertThat(OpportunityKeys.documentId(userId, c.get("canonical_key").asText()))
                    .isEqualTo(c.get("document_id").asText());
        }
    }

    @Test
    void linkedInAlertLinksAreStoredWithoutSignInTokens() {
        String alert = "https://www.linkedin.com/comm/jobs/view/4473143622?trackingId=&refId=&midToken=AQH&otpToken=MDQ0Yj";
        assertThat(OpportunityKeys.canonicalKey(alert)).isEqualTo("linkedin:4473143622");
        assertThat(OpportunityKeys.storedUrl(alert)).isEqualTo("https://www.linkedin.com/jobs/view/4473143622/");
    }
}
