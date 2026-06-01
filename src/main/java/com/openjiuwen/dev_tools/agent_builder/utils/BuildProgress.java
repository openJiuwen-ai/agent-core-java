/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Build progress information.
 * <p>
 * Mirrors Python's {@code BuildProgress} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.progress}.
 */
public class BuildProgress {
    private final String sessionId;
    private final String agentType;
    private AgentBuilderEnums.ProgressStage currentStage;
    private AgentBuilderEnums.ProgressStatus currentStatus;
    private String currentMessage;
    private final List<ProgressStep> steps;
    private double overallProgress;
    private final Instant startTime;
    private Instant lastUpdateTime;
    private String error;

    public BuildProgress(String sessionId, String agentType, AgentBuilderEnums.ProgressStage currentStage,
                         AgentBuilderEnums.ProgressStatus currentStatus, String currentMessage) {
        this(sessionId, agentType, currentStage, currentStatus, currentMessage, List.of());
    }

    public BuildProgress(String sessionId, String agentType, AgentBuilderEnums.ProgressStage currentStage,
                         AgentBuilderEnums.ProgressStatus currentStatus, String currentMessage,
                         List<ProgressStep> steps) {
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.currentStage = currentStage;
        this.currentStatus = currentStatus;
        this.currentMessage = currentMessage;
        this.steps = new ArrayList<>(steps != null ? steps : List.of());
        this.overallProgress = 0.0;
        this.startTime = Instant.now();
        this.lastUpdateTime = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public AgentBuilderEnums.ProgressStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(AgentBuilderEnums.ProgressStage currentStage) {
        this.currentStage = currentStage;
    }

    public AgentBuilderEnums.ProgressStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(AgentBuilderEnums.ProgressStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }

    public void setCurrentMessage(String currentMessage) {
        this.currentMessage = currentMessage;
    }

    public List<ProgressStep> getSteps() {
        return steps;
    }

    public double getOverallProgress() {
        return overallProgress;
    }

    public void setOverallProgress(double overallProgress) {
        this.overallProgress = overallProgress;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("agent_type", agentType);
        result.put("current_stage", currentStage.getValue());
        result.put("current_status", currentStatus.getValue());
        result.put("current_message", currentMessage);
        result.put("steps", steps.stream().map(ProgressStep::toDict).collect(Collectors.toList()));
        result.put("overall_progress", overallProgress);
        result.put("start_time", startTime.toString());
        result.put("last_update_time", lastUpdateTime.toString());
        result.put("error", error);
        return result;
    }
}
