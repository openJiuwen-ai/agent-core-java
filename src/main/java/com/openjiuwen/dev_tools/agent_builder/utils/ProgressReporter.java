/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Progress reporter for agent builder.
 * <p>
 * Mirrors Python's {@code ProgressReporter} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.progress}.
 */
public class ProgressReporter {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressReporter.class);
    private static final Map<String, ProgressReporter> REPORTERS = new ConcurrentHashMap<>();

    private final String sessionId;
    private final String agentType;
    private final BuildProgress progress;
    private final List<Consumer<BuildProgress>> callbacks = new ArrayList<>();
    private final Map<AgentBuilderEnums.ProgressStage, Long> stepStartTimes =
            new EnumMap<>(AgentBuilderEnums.ProgressStage.class);

    public ProgressReporter() {
        this("", "");
    }

    public ProgressReporter(String sessionId, String agentType) {
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.progress = new BuildProgress(
                sessionId,
                agentType,
                AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStatus.PENDING,
                "Initializing..."
        );
    }

    public static ProgressReporter createReporter(String sessionId, String agentType) {
        return REPORTERS.computeIfAbsent(sessionId, key -> new ProgressReporter(sessionId, agentType));
    }

    public static Map<String, Object> getProgress(String sessionId) {
        ProgressReporter reporter = REPORTERS.get(sessionId);
        return reporter != null ? reporter.getProgress().toDict() : null;
    }

    public static void removeReporter(String sessionId) {
        REPORTERS.remove(sessionId);
    }

    public void report(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status,
                        String message) {
        String safeMessage = message != null ? message : "";
        progress.setCurrentStage(stage);
        progress.setCurrentStatus(status);
        progress.setCurrentMessage(safeMessage);
        progress.setLastUpdateTime(Instant.now());
        progress.getSteps().add(new ProgressStep(stage, status, safeMessage));
        LOG.info("[AgentBuilder] {} - {}: {}", stage, status, safeMessage);
        notifyCallbacks();
    }

    public void report(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status) {
        report(stage, status, "");
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public BuildProgress getProgress() {
        return progress;
    }

    public void addCallback(Consumer<BuildProgress> callback) {
        callbacks.add(callback);
    }

    public void removeCallback(Consumer<BuildProgress> callback) {
        callbacks.remove(callback);
    }

    public void startStage(AgentBuilderEnums.ProgressStage stage, String message) {
        startStage(stage, message, Map.of(), null);
    }

    public void startStage(AgentBuilderEnums.ProgressStage stage, String message, Map<String, Object> details) {
        startStage(stage, message, details, null);
    }

    public void startStage(AgentBuilderEnums.ProgressStage stage, String message, Map<String, Object> details,
                           Double progressValue) {
        if (progress.getCurrentStage() != AgentBuilderEnums.ProgressStage.INITIALIZING) {
            endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, null, null, null);
        }

        stepStartTimes.put(stage, System.nanoTime());
        progress.setCurrentStage(stage);
        progress.setCurrentStatus(AgentBuilderEnums.ProgressStatus.RUNNING);
        progress.setCurrentMessage(message);
        progress.setLastUpdateTime(Instant.now());
        progress.setOverallProgress(progressValue != null ? progressValue : calculateProgress(stage));

        ProgressStep step = new ProgressStep(
                stage,
                AgentBuilderEnums.ProgressStatus.RUNNING,
                message,
                details != null ? details : Map.of(),
                null,
                null
        );
        progress.getSteps().add(step);

        LOG.info("[AgentBuilder] Started build stage {} for session {}", stage.getValue(), sessionId);
        notifyCallbacks();
    }

    public void updateStage(String message) {
        updateStage(message, null, null);
    }

    public void updateStage(String message, Map<String, Object> details, Double progressValue) {
        if (message != null && !message.isEmpty()) {
            progress.setCurrentMessage(message);
        }

        if (details != null && !details.isEmpty() && !progress.getSteps().isEmpty()) {
            progress.getSteps().get(progress.getSteps().size() - 1).getDetails().putAll(details);
        }

        if (progressValue != null) {
            progress.setOverallProgress(progressValue);
        }

        progress.setLastUpdateTime(Instant.now());
        notifyCallbacks();
    }

    public void completeStage(String message) {
        completeStage(message, null);
    }

    public void completeStage(String message, Map<String, Object> details) {
        endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, message, details, null);
    }

    public void failStage(String error, String message) {
        failStage(error, message, null);
    }

    public void failStage(String error, String message, Map<String, Object> details) {
        progress.setError(error);
        endCurrentStage(AgentBuilderEnums.ProgressStatus.FAILED, message, details, error);
    }

    public void warnStage(String warning, String message) {
        warnStage(warning, message, null);
    }

    public void warnStage(String warning, String message, Map<String, Object> details) {
        if (message != null && !message.isEmpty()) {
            progress.setCurrentMessage(message);
        }

        if (!progress.getSteps().isEmpty()) {
            ProgressStep step = progress.getSteps().get(progress.getSteps().size() - 1);
            step.setStatus(AgentBuilderEnums.ProgressStatus.WARNING);
            step.getDetails().put("warning", warning);
            if (details != null) {
                step.getDetails().putAll(details);
            }
        }

        progress.setCurrentStatus(AgentBuilderEnums.ProgressStatus.WARNING);
        progress.setLastUpdateTime(Instant.now());
        LOG.warn("[AgentBuilder] Stage warning in session {}: {}", sessionId, warning);
        notifyCallbacks();
    }

    public void complete() {
        complete("Build completed");
    }

    public void complete(String message) {
        progress.setCurrentStage(AgentBuilderEnums.ProgressStage.COMPLETED);
        progress.setCurrentStatus(AgentBuilderEnums.ProgressStatus.SUCCESS);
        progress.setCurrentMessage(message);
        progress.setOverallProgress(100.0);
        progress.setLastUpdateTime(Instant.now());

        if (!progress.getSteps().isEmpty()) {
            endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, message, null, null);
        }

        LOG.info("[AgentBuilder] Build completed for session {}", sessionId);
        notifyCallbacks();
    }

    private void endCurrentStage(AgentBuilderEnums.ProgressStatus status, String message,
                                 Map<String, Object> details, String error) {
        if (progress.getSteps().isEmpty()) {
            return;
        }

        ProgressStep currentStep = progress.getSteps().get(progress.getSteps().size() - 1);
        currentStep.setStatus(status);

        if (message != null && !message.isEmpty()) {
            currentStep.setMessage(message);
        }

        if (details != null) {
            currentStep.getDetails().putAll(details);
        }

        if (error != null) {
            currentStep.setError(error);
            progress.setError(error);
        }

        Long start = stepStartTimes.remove(currentStep.getStage());
        if (start != null) {
            currentStep.setDuration((System.nanoTime() - start) / 1_000_000_000.0);
        }

        progress.setCurrentStatus(status);
        if (message != null && !message.isEmpty()) {
            progress.setCurrentMessage(message);
        }
        progress.setLastUpdateTime(Instant.now());
    }

    private double calculateProgress(AgentBuilderEnums.ProgressStage stage) {
        if (AgentBuilderEnums.AgentType.LLM_AGENT.getValue().equals(agentType)) {
            return switch (stage) {
                case INITIALIZING -> 0.0;
                case CLARIFYING -> 20.0;
                case RESOURCE_RETRIEVING -> 40.0;
                case GENERATING_CONFIG -> 60.0;
                case TRANSFORMING_DSL -> 80.0;
                case COMPLETED -> 100.0;
                default -> 0.0;
            };
        }

        if (AgentBuilderEnums.AgentType.WORKFLOW.getValue().equals(agentType)) {
            return switch (stage) {
                case INITIALIZING -> 0.0;
                case DETECTING_INTENTION -> 10.0;
                case GENERATING_WORKFLOW_DESIGN -> 25.0;
                case GENERATING_DL -> 45.0;
                case VALIDATING_DL -> 60.0;
                case REFINING_DL -> 70.0;
                case TRANSFORMING_MERMAID -> 85.0;
                case TRANSFORMING_WORKFLOW_DSL -> 95.0;
                case COMPLETED -> 100.0;
                default -> 0.0;
            };
        }

        return 0.0;
    }

    private void notifyCallbacks() {
        for (Consumer<BuildProgress> callback : List.copyOf(callbacks)) {
            try {
                callback.accept(progress);
            } catch (Exception e) {
                LOG.error("[AgentBuilder] Progress callback execution failed for session {}", sessionId, e);
            }
        }
    }
}
