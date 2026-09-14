/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code ProgressReporter} in
 * {@code openjiuwen/dev_tools/agent_builder/utils/progress.py}.
 */
public class ProgressReporter {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final String sessionId;
    private final String agentType;
    private final BuildProgress progress;
    private final List<Consumer<BuildProgress>> callbacks;
    private final Map<AgentBuilderEnums.ProgressStage, Double> stepStartTimes;

    public ProgressReporter(String sessionId, String agentType) {
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.progress = new BuildProgress(
                sessionId,
                agentType,
                AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStatus.PENDING,
                "Initializing...");
        this.callbacks = new ArrayList<>();
        this.stepStartTimes = new EnumMap<>(AgentBuilderEnums.ProgressStage.class);
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
        startStage(stage, message, null, null);
    }

    public void startStage(
            AgentBuilderEnums.ProgressStage stage,
            String message,
            Map<String, Object> details) {
        startStage(stage, message, details, null);
    }

    public void startStage(
            AgentBuilderEnums.ProgressStage stage,
            String message,
            Map<String, Object> details,
            Double overallProgress) {
        if (progress.getCurrentStage() != AgentBuilderEnums.ProgressStage.INITIALIZING) {
            endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, null, null, null);
        }

        stepStartTimes.put(stage, nowSeconds());
        progress.setCurrentStage(stage);
        progress.setCurrentStatus(AgentBuilderEnums.ProgressStatus.RUNNING);
        progress.setCurrentMessage(message);
        progress.setLastUpdateTime(OffsetDateTime.now(ZoneOffset.UTC));
        progress.setOverallProgress(overallProgress != null ? overallProgress : calculateProgress(stage));

        ProgressStep step = new ProgressStep(
                stage,
                AgentBuilderEnums.ProgressStatus.RUNNING,
                message,
                details != null ? details : new LinkedHashMap<>());
        progress.getSteps().add(step);

        LOGGER.info(
                "Started build stage session_id={} stage={} message={}",
                sessionId,
                stage.getValue(),
                message);
        notifyCallbacks();
    }

    public void updateStage() {
        updateStage(null, null, null);
    }

    public void updateStage(String message) {
        updateStage(message, null, null);
    }

    public void updateStage(String message, Map<String, Object> details, Double overallProgress) {
        if (message != null) {
            progress.setCurrentMessage(message);
        }

        if (details != null && !progress.getSteps().isEmpty()) {
            progress.getSteps().get(progress.getSteps().size() - 1).getDetails().putAll(details);
        }

        if (overallProgress != null) {
            progress.setOverallProgress(overallProgress);
        }

        progress.setLastUpdateTime(OffsetDateTime.now(ZoneOffset.UTC));
        notifyCallbacks();
    }

    public void completeStage() {
        completeStage(null, null);
    }

    public void completeStage(String message) {
        completeStage(message, null);
    }

    public void completeStage(String message, Map<String, Object> details) {
        endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, message, details, null);
    }

    public void failStage(String error) {
        failStage(error, null, null);
    }

    public void failStage(String error, String message) {
        failStage(error, message, null);
    }

    public void failStage(String error, String message, Map<String, Object> details) {
        progress.setError(error);
        endCurrentStage(AgentBuilderEnums.ProgressStatus.FAILED, message, details, error);
    }

    public void warnStage(String warning) {
        warnStage(warning, null, null);
    }

    public void warnStage(String warning, String message) {
        warnStage(warning, message, null);
    }

    public void warnStage(String warning, String message, Map<String, Object> details) {
        if (message != null) {
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
        progress.setLastUpdateTime(OffsetDateTime.now(ZoneOffset.UTC));
        LOGGER.warning(
                "Build stage warning session_id={} stage={} warning={}",
                sessionId,
                progress.getCurrentStage().getValue(),
                warning);
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
        progress.setLastUpdateTime(OffsetDateTime.now(ZoneOffset.UTC));

        if (!progress.getSteps().isEmpty()) {
            endCurrentStage(AgentBuilderEnums.ProgressStatus.SUCCESS, message, null, null);
        }

        LOGGER.info(
                "Build completed session_id={} agent_type={}",
                sessionId,
                agentType);
        notifyCallbacks();
    }

    private void endCurrentStage(
            AgentBuilderEnums.ProgressStatus status,
            String message,
            Map<String, Object> details,
            String error) {
        if (progress.getSteps().isEmpty()) {
            return;
        }

        ProgressStep currentStep = progress.getSteps().get(progress.getSteps().size() - 1);
        currentStep.setStatus(status);

        if (message != null) {
            currentStep.setMessage(message);
        }
        if (details != null) {
            currentStep.getDetails().putAll(details);
        }
        if (error != null) {
            currentStep.setError(error);
            progress.setError(error);
        }

        Double startTime = stepStartTimes.remove(currentStep.getStage());
        if (startTime != null) {
            currentStep.setDuration(nowSeconds() - startTime);
        }

        progress.setCurrentStatus(status);
        if (message != null) {
            progress.setCurrentMessage(message);
        }
        progress.setLastUpdateTime(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private double calculateProgress(AgentBuilderEnums.ProgressStage stage) {
        Map<AgentBuilderEnums.ProgressStage, Double> llmAgentProgress = Map.of(
                AgentBuilderEnums.ProgressStage.INITIALIZING, 0.0,
                AgentBuilderEnums.ProgressStage.CLARIFYING, 20.0,
                AgentBuilderEnums.ProgressStage.RESOURCE_RETRIEVING, 40.0,
                AgentBuilderEnums.ProgressStage.GENERATING_CONFIG, 60.0,
                AgentBuilderEnums.ProgressStage.TRANSFORMING_DSL, 80.0,
                AgentBuilderEnums.ProgressStage.COMPLETED, 100.0);

        Map<AgentBuilderEnums.ProgressStage, Double> workflowProgress = Map.of(
                AgentBuilderEnums.ProgressStage.INITIALIZING, 0.0,
                AgentBuilderEnums.ProgressStage.DETECTING_INTENTION, 10.0,
                AgentBuilderEnums.ProgressStage.GENERATING_WORKFLOW_DESIGN, 25.0,
                AgentBuilderEnums.ProgressStage.GENERATING_DL, 45.0,
                AgentBuilderEnums.ProgressStage.VALIDATING_DL, 60.0,
                AgentBuilderEnums.ProgressStage.REFINING_DL, 70.0,
                AgentBuilderEnums.ProgressStage.TRANSFORMING_MERMAID, 85.0,
                AgentBuilderEnums.ProgressStage.TRANSFORMING_WORKFLOW_DSL, 95.0,
                AgentBuilderEnums.ProgressStage.COMPLETED, 100.0);

        if (AgentBuilderEnums.AgentType.LLM_AGENT.getValue().equals(agentType)) {
            return llmAgentProgress.getOrDefault(stage, 0.0);
        }
        if (AgentBuilderEnums.AgentType.WORKFLOW.getValue().equals(agentType)) {
            return workflowProgress.getOrDefault(stage, 0.0);
        }
        return 0.0;
    }

    private void notifyCallbacks() {
        for (Consumer<BuildProgress> callback : List.copyOf(callbacks)) {
            try {
                callback.accept(progress);
            } catch (Exception e) {
                LOGGER.error(
                        "Progress callback execution failed error={} callback={} session_id={}",
                        e.toString(),
                        String.valueOf(callback),
                        sessionId);
            }
        }
    }

    private static double nowSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
