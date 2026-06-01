/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progress step information.
 * <p>
 * Mirrors Python's {@code ProgressStep} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.progress}.
 */
public class ProgressStep {
    private final AgentBuilderEnums.ProgressStage stage;
    private AgentBuilderEnums.ProgressStatus status;
    private String message;
    private final Map<String, Object> details;
    private final Instant timestamp;
    private Double duration;
    private String error;

    public ProgressStep(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status,
                        String message) {
        this(stage, status, message, Map.of(), null, null);
    }

    public ProgressStep(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status,
                        String message, Map<String, Object> details) {
        this(stage, status, message, details, null, null);
    }

    public ProgressStep(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status,
                        String message, Map<String, Object> details, Double duration, String error) {
        this.stage = stage;
        this.status = status;
        this.message = message;
        this.details = new LinkedHashMap<>(details != null ? details : Map.of());
        this.timestamp = Instant.now();
        this.duration = duration;
        this.error = error;
    }

    public AgentBuilderEnums.ProgressStage getStage() {
        return stage;
    }

    public AgentBuilderEnums.ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(AgentBuilderEnums.ProgressStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage.getValue());
        result.put("status", status.getValue());
        result.put("message", message);
        result.put("details", new LinkedHashMap<>(details));
        result.put("timestamp", timestamp.toString());
        result.put("duration", duration);
        result.put("error", error);
        return result;
    }
}
