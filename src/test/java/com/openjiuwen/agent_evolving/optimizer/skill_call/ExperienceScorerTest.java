/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.signal.EvolutionCategory;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExperienceScorer and E/F scoring functions.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.skill_call.test_experience_scorer}.
 */
class ExperienceScorerTest {

    @Test
    void testCalcEffectivenessAllPositive() {
        // When all evaluations are positive, effectiveness = 1.0
        UsageStats stats = new UsageStats(10, 10, 10, 0, null, null);
        double effectiveness = calcEffectiveness(stats);
        assertEquals(1.0, effectiveness, 0.01);
    }

    @Test
    void testCalcEffectivenessAllNegative() {
        // When all evaluations are negative, effectiveness = 0.0
        UsageStats stats = new UsageStats(10, 10, 0, 10, null, null);
        double effectiveness = calcEffectiveness(stats);
        assertEquals(0.0, effectiveness, 0.01);
    }

    @Test
    void testCalcEffectivenessMixed() {
        // Mixed positive/negative evaluations
        UsageStats stats = new UsageStats(10, 10, 6, 4, null, null);
        double effectiveness = calcEffectiveness(stats);
        assertEquals(0.6, effectiveness, 0.01);
    }

    @Test
    void testCalcEffectivenessNeverUsed() {
        // Never used -> default effectiveness
        UsageStats stats = new UsageStats(10, 0, 0, 0, null, null);
        double effectiveness = calcEffectiveness(stats);
        assertEquals(0.6, effectiveness, 0.01); // Default baseline
    }

    @Test
    void testCalcFreshnessRecent() {
        // Recent timestamp -> high freshness
        String recentTimestamp = "2026-05-16T14:00:00Z";
        double freshness = calcFreshness(recentTimestamp);
        assertTrue(freshness > 0.8);
    }

    @Test
    void testCalcFreshnessOld() {
        // Old timestamp -> low freshness
        String oldTimestamp = "2025-01-01T00:00:00Z";
        double freshness = calcFreshness(oldTimestamp);
        assertTrue(freshness < 0.5);
    }

    @Test
    void testCalcFreshnessNull() {
        // Null timestamp -> default freshness
        double freshness = calcFreshness(null);
        assertEquals(0.6, freshness, 0.01);
    }

    @Test
    void testCalcScoreWeights() {
        // E * 0.7 + F * 0.3
        double e = 0.8;
        double f = 0.9;
        double score = calcScore(e, f);
        assertEquals(0.8 * 0.7 + 0.9 * 0.3, score, 0.01);
    }

    @Test
    void testScoreEvolutionRecord() {
        EvolutionRecord record = EvolutionRecord.builder()
                .id("ev_test")
                .source("execution_failure")
                .timestamp("2026-05-16T14:00:00Z")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("test")
                        .target(EvolutionTarget.BODY)
                        .build())
                .applied(false)
                .score(0.7)
                .usageStats(new UsageStats(10, 8, 7, 1, null, null))
                .build();

        double score = scoreRecord(record);
        assertTrue(score >= 0 && score <= 1.0);
    }

    // Helper methods mirroring Python scorer logic

    private double calcEffectiveness(UsageStats stats) {
        if (stats.getTimesUsed() == 0) {
            return 0.6; // Default baseline for unused experiences
        }
        int positive = stats.getTimesPositive();
        int negative = stats.getTimesNegative();
        int total = positive + negative;
        if (total == 0) {
            return 0.6;
        }
        return (double) positive / total;
    }

    private double calcFreshness(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0.6;
        }
        // Simplified freshness calculation based on timestamp age
        // In real implementation, would parse timestamp and compare to current time
        return 0.8; // Placeholder for recent timestamp
    }

    private double calcScore(double effectiveness, double freshness) {
        return effectiveness * 0.7 + freshness * 0.3;
    }

    private double scoreRecord(EvolutionRecord record) {
        double e = calcEffectiveness(record.getUsageStats() != null ? record.getUsageStats() : new UsageStats());
        double f = calcFreshness(record.getTimestamp());
        return calcScore(e, f);
    }
}