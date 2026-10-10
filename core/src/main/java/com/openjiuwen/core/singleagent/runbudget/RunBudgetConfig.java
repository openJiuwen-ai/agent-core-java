/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.common.logging.Loggers;

/**
 * Immutable run-budget configuration carried by {@link RunBudgetRail}.
 *
 * <p>Covers both dimensions of FEAT-057: the turn dimension (guaranteed rounds,
 * checkpoint cadence, safety valve) and the time dimension (wall-clock total budget,
 * near-deadline threshold). All validation runs in {@link Builder#build()} so invalid
 * combinations fail fast at wiring time.</p>
 *
 * @since 2026-10-08
 */
public class RunBudgetConfig {
    private static final int DEFAULT_GUARANTEED_ROUNDS = 30;
    private static final int DEFAULT_PROGRESS_INTERVAL_STEP = 10;
    private static final int DEFAULT_MAX_CHECKPOINT_INTERVAL = 50;
    private static final int DEFAULT_STAGNATION_ESCALATION_THRESHOLD = 3;
    private static final int DEFAULT_GENTLE_REMINDER_START_MULTIPLIER = 2;
    private static final int DEFAULT_GENTLE_REMINDER_INTERVAL = 15;
    private static final int DEFAULT_NEAR_DEADLINE_THRESHOLD_SECONDS = 60;
    private static final int MIN_FIRST_CHECKPOINT = 10;
    private static final int HARD_LIMIT_FLOOR = 200;

    private final boolean turnEnabled;
    private final Integer suggestedRounds;
    private final int defaultGuaranteedRounds;
    private final Integer hardLimit;
    private final Integer firstCheckpoint;
    private final int progressIntervalStep;
    private final int maxCheckpointInterval;
    private final int stagnationEscalationThreshold;
    private final int gentleReminderStartMultiplier;
    private final int gentleReminderInterval;
    private final Integer totalBudgetSeconds;
    private final int nearDeadlineThresholdSeconds;

    private RunBudgetConfig(Builder builder) {
        this.turnEnabled = builder.turnEnabled;
        this.suggestedRounds = builder.suggestedRounds;
        this.defaultGuaranteedRounds = builder.defaultGuaranteedRounds;
        this.hardLimit = builder.hardLimit;
        this.firstCheckpoint = builder.firstCheckpoint;
        this.progressIntervalStep = builder.progressIntervalStep;
        this.maxCheckpointInterval = builder.maxCheckpointInterval;
        this.stagnationEscalationThreshold = builder.stagnationEscalationThreshold;
        this.gentleReminderStartMultiplier = builder.gentleReminderStartMultiplier;
        this.gentleReminderInterval = builder.gentleReminderInterval;
        this.totalBudgetSeconds = builder.totalBudgetSeconds;
        this.nearDeadlineThresholdSeconds = builder.nearDeadlineThresholdSeconds;
    }

    /**
     * Creates a builder with default cadence values.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether the turn dimension is active.
     *
     * @return true when the turn dimension is active
     */
    public boolean isTurnEnabled() {
        return turnEnabled;
    }

    /**
     * Returns whether the time dimension is active.
     *
     * @return true when a total time budget is declared
     */
    public boolean isTimeEnabled() {
        return totalBudgetSeconds != null;
    }

    /**
     * Returns the explicitly suggested rounds, or null when undeclared.
     *
     * @return suggested rounds or null
     */
    public Integer getSuggestedRounds() {
        return suggestedRounds;
    }

    /**
     * Returns the default guaranteed rounds used when no suggestion is declared.
     *
     * @return default guaranteed rounds
     */
    public int getDefaultGuaranteedRounds() {
        return defaultGuaranteedRounds;
    }

    /**
     * Returns the explicit absolute hard limit, or null for the derived default.
     *
     * @return hard limit or null
     */
    public Integer getHardLimit() {
        return hardLimit;
    }

    /**
     * Returns the explicit first checkpoint, or null for the derived default.
     *
     * @return first checkpoint or null
     */
    public Integer getFirstCheckpoint() {
        return firstCheckpoint;
    }

    /**
     * Returns the per-progress checkpoint interval step.
     *
     * @return interval step in rounds
     */
    public int getProgressIntervalStep() {
        return progressIntervalStep;
    }

    /**
     * Returns the checkpoint interval ceiling.
     *
     * @return maximum checkpoint interval in rounds
     */
    public int getMaxCheckpointInterval() {
        return maxCheckpointInterval;
    }

    /**
     * Returns the consecutive-stall count that escalates prompt strength.
     *
     * @return escalation threshold in checkpoints
     */
    public int getStagnationEscalationThreshold() {
        return stagnationEscalationThreshold;
    }

    /**
     * Returns the guarantee multiplier at which gentle reminders start.
     *
     * @return start multiplier
     */
    public int getGentleReminderStartMultiplier() {
        return gentleReminderStartMultiplier;
    }

    /**
     * Returns the gentle reminder interval.
     *
     * @return reminder interval in rounds
     */
    public int getGentleReminderInterval() {
        return gentleReminderInterval;
    }

    /**
     * Returns the wall-clock total budget in seconds, or null when undeclared.
     *
     * @return total budget seconds or null
     */
    public Integer getTotalBudgetSeconds() {
        return totalBudgetSeconds;
    }

    /**
     * Returns the near-deadline threshold in seconds (clamped to the total budget).
     *
     * @return near-deadline threshold seconds
     */
    public int getNearDeadlineThresholdSeconds() {
        return nearDeadlineThresholdSeconds;
    }

    /**
     * Builder for {@link RunBudgetConfig}; validates all values in {@link #build()}.
     *
     * @since 2026-10-08
     */
    public static final class Builder {
        private boolean turnEnabled;
        private Integer suggestedRounds;
        private int defaultGuaranteedRounds = DEFAULT_GUARANTEED_ROUNDS;
        private Integer hardLimit;
        private Integer firstCheckpoint;
        private int progressIntervalStep = DEFAULT_PROGRESS_INTERVAL_STEP;
        private int maxCheckpointInterval = DEFAULT_MAX_CHECKPOINT_INTERVAL;
        private int stagnationEscalationThreshold = DEFAULT_STAGNATION_ESCALATION_THRESHOLD;
        private int gentleReminderStartMultiplier = DEFAULT_GENTLE_REMINDER_START_MULTIPLIER;
        private int gentleReminderInterval = DEFAULT_GENTLE_REMINDER_INTERVAL;
        private Integer totalBudgetSeconds;
        private int nearDeadlineThresholdSeconds = DEFAULT_NEAR_DEADLINE_THRESHOLD_SECONDS;

        private Builder() {
        }

        /**
         * Sets whether the turn dimension is active (decided by host wiring gating,
         * defaults to off).
         *
         * @param enabled true to activate the turn dimension
         * @return this builder
         */
        public Builder turnEnabled(boolean enabled) {
            this.turnEnabled = enabled;
            return this;
        }

        /**
         * Sets the suggested rounds (guarantee); null means the default applies.
         *
         * @param rounds suggested rounds, must be positive when non-null
         * @return this builder
         */
        public Builder suggestedRounds(Integer rounds) {
            this.suggestedRounds = rounds;
            return this;
        }

        /**
         * Sets the default guaranteed rounds.
         *
         * @param rounds default guarantee, must be positive
         * @return this builder
         */
        public Builder defaultGuaranteedRounds(int rounds) {
            this.defaultGuaranteedRounds = rounds;
            return this;
        }

        /**
         * Sets the explicit absolute hard limit; null derives {@code max(guarantee * 2, 200)}.
         *
         * @param limit hard limit, must be no less than the guarantee when non-null
         * @return this builder
         */
        public Builder hardLimit(Integer limit) {
            this.hardLimit = limit;
            return this;
        }

        /**
         * Sets the explicit first checkpoint; null derives {@code max(10, guarantee)}.
         *
         * @param checkpoint first checkpoint round
         * @return this builder
         */
        public Builder firstCheckpoint(Integer checkpoint) {
            this.firstCheckpoint = checkpoint;
            return this;
        }

        /**
         * Sets the per-progress checkpoint interval step.
         *
         * @param step interval step in rounds, must be positive
         * @return this builder
         */
        public Builder progressIntervalStep(int step) {
            this.progressIntervalStep = step;
            return this;
        }

        /**
         * Sets the checkpoint interval ceiling.
         *
         * @param maxInterval maximum interval in rounds, must be positive
         * @return this builder
         */
        public Builder maxCheckpointInterval(int maxInterval) {
            this.maxCheckpointInterval = maxInterval;
            return this;
        }

        /**
         * Sets the consecutive-stall count that escalates prompt strength.
         *
         * @param threshold escalation threshold, must be positive
         * @return this builder
         */
        public Builder stagnationEscalationThreshold(int threshold) {
            this.stagnationEscalationThreshold = threshold;
            return this;
        }

        /**
         * Sets the guarantee multiplier at which gentle reminders start.
         *
         * @param multiplier start multiplier, must be positive
         * @return this builder
         */
        public Builder gentleReminderStartMultiplier(int multiplier) {
            this.gentleReminderStartMultiplier = multiplier;
            return this;
        }

        /**
         * Sets the gentle reminder interval.
         *
         * @param interval reminder interval in rounds, must be positive
         * @return this builder
         */
        public Builder gentleReminderInterval(int interval) {
            this.gentleReminderInterval = interval;
            return this;
        }

        /**
         * Sets the wall-clock total budget in seconds; null disables the time dimension.
         *
         * @param seconds total budget, must be positive when non-null
         * @return this builder
         */
        public Builder totalBudgetSeconds(Integer seconds) {
            this.totalBudgetSeconds = seconds;
            return this;
        }

        /**
         * Sets the near-deadline threshold in seconds.
         *
         * @param seconds threshold, must be positive; clamped to the total budget
         * @return this builder
         */
        public Builder nearDeadlineThresholdSeconds(int seconds) {
            this.nearDeadlineThresholdSeconds = seconds;
            return this;
        }

        /**
         * Validates and builds the immutable config.
         *
         * @return the validated config
         * @throws IllegalArgumentException when any value is illegal
         */
        public RunBudgetConfig build() {
            int effectiveGuarantee = validateGuaranteeInputs();
            int effectiveHardLimit = validateHardLimit(effectiveGuarantee);
            validateFirstCheckpoint(effectiveGuarantee);
            validateCadence();
            validateTimeInputs();
            warnOnDegenerateCombinations(effectiveGuarantee, effectiveHardLimit);
            return new RunBudgetConfig(this);
        }

        private int validateGuaranteeInputs() {
            if (suggestedRounds != null && suggestedRounds <= 0) {
                throw new IllegalArgumentException("run-budget suggestedRounds must be positive");
            }
            if (defaultGuaranteedRounds <= 0) {
                throw new IllegalArgumentException("run-budget defaultGuaranteedRounds must be positive");
            }
            return suggestedRounds != null ? suggestedRounds : defaultGuaranteedRounds;
        }

        private int validateHardLimit(int effectiveGuarantee) {
            if (hardLimit != null && hardLimit <= 0) {
                throw new IllegalArgumentException("run-budget hardLimit must be positive");
            }
            if (hardLimit != null && hardLimit < effectiveGuarantee) {
                throw new IllegalArgumentException("run-budget hardLimit must be no less than the guarantee");
            }
            return hardLimit != null ? hardLimit : Math.max(effectiveGuarantee * 2, HARD_LIMIT_FLOOR);
        }

        private void validateFirstCheckpoint(int effectiveGuarantee) {
            int minimum = Math.max(MIN_FIRST_CHECKPOINT, effectiveGuarantee);
            if (firstCheckpoint != null && firstCheckpoint < minimum) {
                throw new IllegalArgumentException(
                        "run-budget firstCheckpoint must be no less than max(10, guarantee)");
            }
        }

        private void validateCadence() {
            if (progressIntervalStep <= 0 || maxCheckpointInterval <= 0) {
                throw new IllegalArgumentException("run-budget checkpoint interval values must be positive");
            }
            if (stagnationEscalationThreshold <= 0 || gentleReminderStartMultiplier <= 0
                    || gentleReminderInterval <= 0) {
                throw new IllegalArgumentException("run-budget stall and reminder values must be positive");
            }
        }

        private void validateTimeInputs() {
            if (totalBudgetSeconds != null && totalBudgetSeconds <= 0) {
                throw new IllegalArgumentException("run-budget totalBudgetSeconds must be positive");
            }
            if (nearDeadlineThresholdSeconds <= 0) {
                throw new IllegalArgumentException("run-budget nearDeadlineThresholdSeconds must be positive");
            }
            if (totalBudgetSeconds != null && nearDeadlineThresholdSeconds > totalBudgetSeconds) {
                Loggers.AGENT.warning("run-budget nearDeadlineThresholdSeconds exceeds totalBudgetSeconds; "
                        + "clamped to the total budget");
                nearDeadlineThresholdSeconds = totalBudgetSeconds;
            }
        }

        private void warnOnDegenerateCombinations(int effectiveGuarantee, int effectiveHardLimit) {
            if (hardLimit != null && hardLimit == effectiveGuarantee) {
                Loggers.AGENT.warning("run-budget hardLimit equals the guarantee; elastic budget degrades "
                        + "to guarantee plus immediate safety valve");
            }
            int effectiveFirstCheckpoint = firstCheckpoint != null
                    ? firstCheckpoint : Math.max(MIN_FIRST_CHECKPOINT, effectiveGuarantee);
            if (effectiveFirstCheckpoint > effectiveHardLimit) {
                Loggers.AGENT.warning("run-budget firstCheckpoint exceeds hardLimit; "
                        + "checkpoint evaluation is unreachable");
            }
        }
    }
}
