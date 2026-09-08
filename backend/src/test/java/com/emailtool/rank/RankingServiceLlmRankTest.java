package com.emailtool.rank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankingServiceLlmRankTest {

    @Test
    void skipsSingletonAndHugeBatches() {
        assertFalse(RankingService.shouldLlmRank(0));
        assertFalse(RankingService.shouldLlmRank(1));
        assertTrue(RankingService.shouldLlmRank(2));
        assertTrue(RankingService.shouldLlmRank(20));
        assertFalse(RankingService.shouldLlmRank(21));
    }
}
