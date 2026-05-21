/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Experience scoring utilities for skill evolution.
 * <p>
 * Provides E/U/F score calculation functions and constants.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call.experience_scorer}.
 */
public final class ExperienceScorerUtils {

    private ExperienceScorerUtils() {
        // Utility class
    }

    // E/U/F score weights
    public static final double W_E = 0.5;
    public static final double W_U = 0.3;
    public static final double W_F = 0.2;

    // Freshness decay configuration
    public static final double FRESHNESS_HALF_LIFE_DAYS = 90;
    public static final double STALE_VERSION_PENALTY = 0.7;

    /**
     * Calculate E (Effectiveness) score using Bayesian smoothing.
     * Uses Beta(1,1) prior for smoothing, equivalent to adding 1 success and 1 failure.
     *
     * @param stats Usage statistics
     * @return Effectiveness score (0.5 to 1.0)
     */
    public static double calcEffectiveness(UsageStats stats) {
        if (stats == null) {
            return 0.5; // No data, return neutral
        }
        long total = stats.getTimesPositive() + stats.getTimesNegative();
        if (total == 0) {
            return 0.5; // No data, return neutral
        }
        // Beta(1,1) prior: (positive + 1) / (total + 2)
        return (stats.getTimesPositive() + 1.0) / (total + 2.0);
    }

    /**
     * Calculate U (Utilization) score: ratio of times_used to times_presented.
     *
     * @param stats Usage statistics
     * @return Utilization score (0.0 to 1.0)
     */
    public static double calcUtilization(UsageStats stats) {
        if (stats == null || stats.getTimesPresented() == 0) {
            return 0.5; // No data, return neutral
        }
        return (double) stats.getTimesUsed() / stats.getTimesPresented();
    }

    /**
     * Calculate F (Freshness) score based on time decay and version staleness.
     * Applies exponential decay with configurable half-life, plus version staleness penalty.
     *
     * @param record Evolution record
     * @param currentSkillVersion Current skill version (optional)
     * @return Freshness score (0.0 to 1.0)
     */
    public static double calcFreshness(EvolutionRecord record, String currentSkillVersion) {
        if (record == null || record.getTimestamp() == null) {
            return 0.5;
        }

        try {
            String timestampStr = record.getTimestamp().replace("Z", "+00:00");
            Instant recordTime = Instant.parse(timestampStr);
            Instant now = Instant.now();
            long daysOld = Duration.between(recordTime, now).toDays();

            // Exponential decay: score = 0.5 * 2^(-days / half_life)
            double decayFactor = 0.5 * Math.pow(2, -daysOld / FRESHNESS_HALF_LIFE_DAYS);
            double freshness = 0.5 + decayFactor; // Range: 0.5 to 1.0

            // Apply version staleness penalty if skill version is known
            if (currentSkillVersion != null && record.getSkillVersion() != null) {
                if (!record.getSkillVersion().equals(currentSkillVersion)) {
                    freshness *= STALE_VERSION_PENALTY;
                }
            }

            return Math.max(0.0, Math.min(1.0, freshness));
        } catch (DateTimeParseException e) {
            return 0.5;
        }
    }

    /**
     * Calculate overall score as weighted sum of E/U/F components.
     *
     * @param record Evolution record
     * @param currentSkillVersion Current skill version (optional)
     * @return Overall score (0.0 to 1.0)
     */
    public static double calcScore(EvolutionRecord record, String currentSkillVersion) {
        UsageStats stats = record != null ? record.getUsageStats() : null;

        double e = calcEffectiveness(stats);
        double u = calcUtilization(stats);
        double f = calcFreshness(record, currentSkillVersion);

        return W_E * e + W_U * u + W_F * f;
    }

    /**
     * Update record's usage stats and recalculate score based on evaluation result.
     *
     * @param record The evolution record to update
     * @param evalResult Dict with "used", "positive", "negative" boolean keys
     * @param currentSkillVersion Optional current skill version
     * @return The new calculated score
     */
    public static double updateScore(EvolutionRecord record, java.util.Map<String, Object> evalResult,
                                     String currentSkillVersion) {
        if (record == null) {
            return 0.5;
        }

        UsageStats stats = record.getUsageStats();
        if (stats == null) {
            stats = new UsageStats();
            record.setUsageStats(stats);
        }

        if (evalResult != null) {
            if (Boolean.TRUE.equals(evalResult.get("used"))) {
                stats.setTimesUsed(stats.getTimesUsed() + 1);
            }
            if (Boolean.TRUE.equals(evalResult.get("positive"))) {
                stats.setTimesPositive(stats.getTimesPositive() + 1);
            }
            if (Boolean.TRUE.equals(evalResult.get("negative"))) {
                stats.setTimesNegative(stats.getTimesNegative() + 1);
            }
            stats.setLastEvaluatedAt(Instant.now().toString());
        }

        double newScore = calcScore(record, currentSkillVersion);
        record.setScore(newScore);
        return newScore;
    }
}