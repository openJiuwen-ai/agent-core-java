/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task-completion strategy Rail.
 * <p>
 * Carries loop-strategy parameters and implements the lifecycle hooks
 * that drive completion detection and prompt injection.
 * <p>
 * Mirrors Python's {@code TaskCompletionRail} in
 * {@code openjiuwen.harness.rails.task_completion_rail}.
 */
public class TaskCompletionRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TaskCompletionRail.class);

    /** Rail priority (low = runs first). */
    public static final int PRIORITY = 10;

    /** Promise tag pattern for completion detection. */
    private static final Pattern PROMISE_TAG_PATTERN =
            Pattern.compile("<promise>\\s*(.*?)\\s*</promise>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final String taskInstruction;
    private final String completionPromise;
    private final int requiredConfirmations;
    private final boolean allowPromiseDetails;
    private final Integer maxRounds;
    private final Double timeoutSeconds;
    private final List<StopConditionEvaluator> evaluators = new ArrayList<>();

    private int confirmCount = 0;
    private int iterationCount = 0;
    private long startTimeMs;

    /**
     * Create a TaskCompletionRail.
     *
     * @param taskInstruction      format string with {query} placeholder
     * @param completionPromise    token the model outputs to signal completion
     * @param requiredConfirmations number of confirmations needed
     * @param allowPromiseDetails  whether to allow details inside promise tags
     * @param maxRounds            max outer-loop rounds
     * @param timeoutSeconds       wall-clock timeout
     */
    public TaskCompletionRail(String taskInstruction, String completionPromise,
                               int requiredConfirmations, boolean allowPromiseDetails,
                               Integer maxRounds, Double timeoutSeconds) {
        super();
        this.taskInstruction = taskInstruction;
        this.completionPromise = completionPromise;
        this.requiredConfirmations = requiredConfirmations;
        this.allowPromiseDetails = allowPromiseDetails;
        this.maxRounds = maxRounds;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Detect completion promise in model output. */
    public Optional<String> detectPromise(String output) {
        if (completionPromise == null || output == null) {
            return Optional.empty();
        }
        Matcher m = PROMISE_TAG_PATTERN.matcher(output);
        while (m.find()) {
            String content = m.group(1).trim();
            if (content.equalsIgnoreCase(completionPromise)) {
                confirmCount++;
                LOG.info("[TaskCompletionRail] Completion promise detected ({}/{})",
                        confirmCount, requiredConfirmations);
                if (confirmCount >= requiredConfirmations) {
                    return Optional.of(content);
                }
            }
        }
        return Optional.empty();
    }

    /** Check if max rounds exceeded. */
    public boolean isMaxRoundsExceeded() {
        return maxRounds != null && iterationCount >= maxRounds;
    }

    /** Check if timeout exceeded. */
    public boolean isTimeoutExceeded() {
        if (timeoutSeconds == null) {
            return false;
        }
        return (System.currentTimeMillis() - startTimeMs) / 1000.0 > timeoutSeconds;
    }

    /** Increment iteration counter. */
    public void incrementIteration() {
        iterationCount++;
    }

    @Override
    public void init(Object agent) {
        this.startTimeMs = System.currentTimeMillis();
        LOG.info("[TaskCompletionRail] Initialized (maxRounds={}, timeout={}s)",
                maxRounds, timeoutSeconds);
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TaskCompletionRail] Uninitialized after {} iterations", iterationCount);
    }

    /** Functional interface for stop condition evaluation. */
    @FunctionalInterface
    public interface StopConditionEvaluator {
        /** Return true if the loop should stop. */
        boolean shouldStop(int iteration, String lastOutput);
    }
}
