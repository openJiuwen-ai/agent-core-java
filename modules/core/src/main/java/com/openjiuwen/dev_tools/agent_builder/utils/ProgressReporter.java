/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Public class ProgressReporter used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ProgressReporter {
    private final String sessionId;
    private final String agentType;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Consumer<BuildProgress>> callbacks = new ArrayList<>();
    private Instant stageStartTime;
    private final BuildProgress progress;

    /**
     * ProgressReporter.
     * 
     * @param sessionId sessionId
     * @param agentType agentType
     * @since 0.1.7
     */
    public ProgressReporter(String sessionId, String agentType) {
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.progress =
            BuildProgress.builder().sessionId(sessionId).agentType(agentType).currentStage(ProgressStage.INITIALIZING)
                    .currentStatus(ProgressStatus.PENDING).currentMessage("Initializing...").build();
    }

    /**
     * addCallback.
     * 
     * @param callback callback
     * @since 0.1.7
     */
    public void addCallback(Consumer<BuildProgress> callback) {
        callbacks.add(callback);
    }

    /**
     * removeCallback.
     * 
     * @param callback callback
     * @since 0.1.7
     */
    public void removeCallback(Consumer<BuildProgress> callback) {
        callbacks.remove(callback);
    }

    /**
     * startStage.
     * 
     * @param stage stage
     * @param message message
     * @since 0.1.7
     */
    public void startStage(ProgressStage stage, String message) {
        startStage(stage, message, Map.of(), progress.getOverallProgress());
    }

    /**
     * startStage.
     * 
     * @param stage stage
     * @param message message
     * @param details details
     * @param overallProgress overallProgress
     * @since 0.1.7
     */
    public void startStage(ProgressStage stage, String message, Map<String, Object> details, Double overallProgress) {
        stageStartTime = Instant.now();
        ProgressStep step = ProgressStep.builder().stage(stage).status(ProgressStatus.RUNNING).message(message)
                .details(details).timestamp(stageStartTime).build();
        progress.getSteps().add(step);
        progress.setCurrentStage(stage);
        progress.setCurrentStatus(ProgressStatus.RUNNING);
        progress.setCurrentMessage(message);
        progress.setOverallProgress(overallProgress);
        progress.setLastUpdateTime(stageStartTime);
        notifyCallbacks();
    }

    /**
     * completeStage.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void completeStage(String message) {
        completeStage(message, Map.of());
    }

    /**
     * completeStage.
     * 
     * @param message message
     * @param details details
     * @since 0.1.7
     */
    public void completeStage(String message, Map<String, Object> details) {
        ProgressStep step = latestStep();
        Instant now = Instant.now();
        if (step != null) {
            step.setStatus(ProgressStatus.SUCCESS);
            step.setMessage(message);
            step.setDetails(details);
            step.setDuration(
                    stageStartTime != null ? (double) Duration.between(stageStartTime, now).toMillis() / 1000.0 : null);
        }
        progress.setCurrentStatus(ProgressStatus.SUCCESS);
        progress.setCurrentMessage(message);
        progress.setLastUpdateTime(now);
        notifyCallbacks();
    }

    /**
     * failStage.
     * 
     * @param error error
     * @param message message
     * @since 0.1.7
     */
    public void failStage(String error, String message) {
        ProgressStep step = latestStep();
        Instant now = Instant.now();
        if (step != null) {
            step.setStatus(ProgressStatus.FAILED);
            step.setMessage(message);
            step.setError(error);
            step.setDuration(
                    stageStartTime != null ? (double) Duration.between(stageStartTime, now).toMillis() / 1000.0 : null);
        }
        progress.setCurrentStage(ProgressStage.ERROR);
        progress.setCurrentStatus(ProgressStatus.FAILED);
        progress.setCurrentMessage(message);
        progress.setError(error);
        progress.setLastUpdateTime(now);
        notifyCallbacks();
    }

    /**
     * complete.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void complete(String message) {
        progress.setCurrentStage(ProgressStage.COMPLETED);
        progress.setCurrentStatus(ProgressStatus.SUCCESS);
        progress.setCurrentMessage(message);
        progress.setOverallProgress(100.0);
        progress.setLastUpdateTime(Instant.now());
        notifyCallbacks();
    }

    /**
     * getProgress.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BuildProgress getProgress() {
        return progress;
    }

    /**
     * latestStep.
     * 
     * @return the result
     * @since 0.1.7
     */
    private ProgressStep latestStep() {
        if (progress.getSteps().isEmpty()) {
            return null;
        }
        return progress.getSteps().get(progress.getSteps().size() - 1);
    }

    /**
     * notifyCallbacks.
     * 
     * @since 0.1.7
     */
    private void notifyCallbacks() {
        for (Consumer<BuildProgress> callback : callbacks) {
            callback.accept(progress);
        }
    }
}
