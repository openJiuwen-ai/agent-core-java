/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured metrics tracker for RL training.
 * <p>
 * Mirrors Python's {@code RLMetricsTracker} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.metrics_tracker}.
 */
public class RLMetricsTracker {

    private final Map<String, Object> initKwargs = new LinkedHashMap<>();
    private final List<Map<String, Object>> loggedMetrics = new ArrayList<>();
    private final List<Integer> loggedSteps = new ArrayList<>();
    private boolean initialized;
    private boolean finished;

    public RLMetricsTracker(String projectName, String experimentName, List<String> backends, Map<String, Object> config) {
        initKwargs.put("project_name", projectName);
        initKwargs.put("experiment_name", experimentName);
        initKwargs.put("default_backend", backends);
        initKwargs.put("config", config);
    }

    public RLMetricsTracker(String projectName, String experimentName, List<String> backends) {
        this(projectName, experimentName, backends, null);
    }

    public void logStep(int step, Map<String, Object> metrics) {
        ensureInitialized();
        loggedSteps.add(step);
        loggedMetrics.add(metrics != null ? new LinkedHashMap<>(metrics) : new LinkedHashMap<>());
    }

    public void logTrainingStep(TrainingStepMetrics data) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (data != null && data.getVerlMetrics() != null) {
            metrics.putAll(data.getVerlMetrics());
        }
        if (data != null) {
            metrics.put("training/global_step", data.getStep());
            metrics.put("training/epoch", data.getEpoch());
            metrics.put("training/avg_conversation_turns", data.getAvgTurns());
            metrics.put("training/rollout_reward_mean", data.getRewardMean());
            metrics.put("training/consecutive_zero_reward_steps", data.getConsecutiveZeroRewardSteps());
            logStep(data.getStep(), metrics);
        }
    }

    public void logTrainingStep(Map<String, Object> metrics) {
        int step = intValue(metrics != null ? metrics.get("training/global_step") : null, 0);
        logStep(step, metrics);
    }

    public void logRolloutStats(int step,
                                Map<String, ? extends List<? extends Map<String, Object>>> rewardsByUid,
                                int totalPositive,
                                int totalNegative) {
        logRolloutStats(step, rewardsByUid, totalPositive, totalNegative, null);
    }

    public void logRolloutStats(int step,
                                Map<String, ? extends List<? extends Map<String, Object>>> rewardsByUid,
                                int totalPositive,
                                int totalNegative,
                                Integer totalTrainingSamples) {
        List<Double> rewards = new ArrayList<>();
        if (rewardsByUid != null) {
            for (List<? extends Map<String, Object>> entries : rewardsByUid.values()) {
                if (entries == null) {
                    continue;
                }
                for (Map<String, Object> entry : entries) {
                    Double value = doubleValue(entry != null ? entry.get("global") : null);
                    if (value != null) {
                        rewards.add(value);
                    }
                }
            }
        }
        if (rewards.isEmpty()) {
            return;
        }

        double mean = rewards.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double variance = rewards.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0d);
        int total = totalPositive + totalNegative;
        Map<String, Object> rolloutMetrics = new LinkedHashMap<>();
        rolloutMetrics.put("rollout/reward_mean", mean);
        rolloutMetrics.put("rollout/reward_std", Math.sqrt(variance));
        rolloutMetrics.put("rollout/reward_max", rewards.stream().mapToDouble(Double::doubleValue).max().orElse(0.0d));
        rolloutMetrics.put("rollout/reward_min", rewards.stream().mapToDouble(Double::doubleValue).min().orElse(0.0d));
        rolloutMetrics.put("rollout/positive_ratio", totalPositive / (double) Math.max(total, 1));
        rolloutMetrics.put("rollout/total_rollouts", totalTrainingSamples != null ? totalTrainingSamples : rewards.size());
        rolloutMetrics.put("rollout/unique_prompts", rewardsByUid != null ? rewardsByUid.size() : 0);
        logStep(step, rolloutMetrics);
    }

    public void logRewardDistribution(int step, List<Double> rewards) {
        ensureInitialized();
    }

    public void logValidation(int step, Map<String, Object> valMetrics) {
        logStep(step, valMetrics);
    }

    public void finish() {
        finished = true;
    }

    public Map<String, Object> getInitKwargs() {
        return initKwargs;
    }

    public List<Map<String, Object>> getLoggedMetrics() {
        return new ArrayList<>(loggedMetrics);
    }

    public List<Integer> getLoggedSteps() {
        return new ArrayList<>(loggedSteps);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isFinished() {
        return finished;
    }

    private void ensureInitialized() {
        initialized = true;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Encapsulates all metrics for a single training step log entry.
     */
    public static final class TrainingStepMetrics {
        private final int step;
        private final int epoch;
        private final Map<String, Object> verlMetrics;
        private final double avgTurns;
        private final double rewardMean;
        private final int consecutiveZeroRewardSteps;

        public TrainingStepMetrics(int step,
                                   int epoch,
                                   Map<String, Object> verlMetrics,
                                   double avgTurns,
                                   double rewardMean,
                                   int consecutiveZeroRewardSteps) {
            this.step = step;
            this.epoch = epoch;
            this.verlMetrics = verlMetrics != null ? new LinkedHashMap<>(verlMetrics) : new LinkedHashMap<>();
            this.avgTurns = avgTurns;
            this.rewardMean = rewardMean;
            this.consecutiveZeroRewardSteps = consecutiveZeroRewardSteps;
        }

        public int getStep() {
            return step;
        }

        public int getEpoch() {
            return epoch;
        }

        public Map<String, Object> getVerlMetrics() {
            return new LinkedHashMap<>(verlMetrics);
        }

        public double getAvgTurns() {
            return avgTurns;
        }

        public double getRewardMean() {
            return rewardMean;
        }

        public int getConsecutiveZeroRewardSteps() {
            return consecutiveZeroRewardSteps;
        }
    }
}
