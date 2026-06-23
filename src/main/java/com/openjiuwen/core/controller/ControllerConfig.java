/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller configuration.
 * <p>
 * Mirrors Python's {@code ControllerConfig} in
 * {@code openjiuwen/core/controller/config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ControllerConfig {

    @JsonProperty("max_concurrent_tasks")
    private int maxConcurrentTasks = 5;

    @JsonProperty("schedule_interval")
    private double scheduleInterval = 1.0;

    @JsonProperty("task_timeout")
    private Double taskTimeout;

    @JsonProperty("default_task_priority")
    private int defaultTaskPriority = 1;

    @JsonProperty("enable_task_persistence")
    private boolean enableTaskPersistence = false;

    @JsonProperty("event_queue_size")
    private Integer eventQueueSize = 10000;

    @JsonProperty("event_timeout")
    private Double eventTimeout = 300.0;

    @JsonProperty("enable_intent_recognition")
    private boolean enableIntentRecognition = false;

    @JsonProperty("intent_llm_id")
    private String intentLlmId = "";

    @JsonProperty("intent_confidence_threshold")
    private double intentConfidenceThreshold = 0.7;

    @JsonProperty("intent_type_list")
    private List<String> intentTypeList = new ArrayList<>(List.of(
            "create_task",
            "pause_task",
            "resume_task",
            "cancel_task",
            "unknown_task"
    ));

    @JsonProperty("default_response")
    private DefaultResponse defaultResponse = new DefaultResponse();

    @JsonProperty("suppress_completion_signal")
    private boolean suppressCompletionSignal = false;

    @JsonProperty("stream_first_frame_timeout")
    private Double streamFirstFrameTimeout = 30.0;

    /**
     * Mirrors Python's {@code DefaultResponse} in
     * {@code openjiuwen/core/controller/config.py}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DefaultResponse {

        private String type = "text";
        private String text;

        public DefaultResponse() {
        }

        public DefaultResponse(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            if (!"text".equals(type) && !"workflow".equals(type)) {
                throw new IllegalArgumentException("defaultResponse.type must be 'text' or 'workflow'");
            }
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public double getScheduleInterval() {
        return scheduleInterval;
    }

    public void setScheduleInterval(double scheduleInterval) {
        if (scheduleInterval < 0.1) {
            throw new IllegalArgumentException("scheduleInterval must be >= 0.1");
        }
        this.scheduleInterval = scheduleInterval;
    }

    public Double getTaskTimeout() {
        return taskTimeout;
    }

    public void setTaskTimeout(Double taskTimeout) {
        if (taskTimeout != null && taskTimeout <= 0) {
            throw new IllegalArgumentException("taskTimeout must be positive or null");
        }
        this.taskTimeout = taskTimeout;
    }

    public int getDefaultTaskPriority() {
        return defaultTaskPriority;
    }

    public void setDefaultTaskPriority(int defaultTaskPriority) {
        this.defaultTaskPriority = defaultTaskPriority;
    }

    public boolean isEnableTaskPersistence() {
        return enableTaskPersistence;
    }

    public void setEnableTaskPersistence(boolean enableTaskPersistence) {
        this.enableTaskPersistence = enableTaskPersistence;
    }

    public Integer getEventQueueSize() {
        return eventQueueSize;
    }

    public void setEventQueueSize(Integer eventQueueSize) {
        if (eventQueueSize != null && eventQueueSize < 1) {
            throw new IllegalArgumentException("eventQueueSize must be >= 1 or null");
        }
        this.eventQueueSize = eventQueueSize;
    }

    public Double getEventTimeout() {
        return eventTimeout;
    }

    public void setEventTimeout(Double eventTimeout) {
        if (eventTimeout != null && eventTimeout < 100) {
            throw new IllegalArgumentException("eventTimeout must be >= 100 or null");
        }
        this.eventTimeout = eventTimeout;
    }

    public boolean isEnableIntentRecognition() {
        return enableIntentRecognition;
    }

    public void setEnableIntentRecognition(boolean enableIntentRecognition) {
        this.enableIntentRecognition = enableIntentRecognition;
    }

    public String getIntentLlmId() {
        return intentLlmId;
    }

    public void setIntentLlmId(String intentLlmId) {
        this.intentLlmId = intentLlmId == null ? "" : intentLlmId;
    }

    public double getIntentConfidenceThreshold() {
        return intentConfidenceThreshold;
    }

    public void setIntentConfidenceThreshold(double intentConfidenceThreshold) {
        if (intentConfidenceThreshold < 0.0 || intentConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException("intentConfidenceThreshold must be between 0.0 and 1.0");
        }
        this.intentConfidenceThreshold = intentConfidenceThreshold;
    }

    public List<String> getIntentTypeList() {
        return intentTypeList;
    }

    public void setIntentTypeList(List<String> intentTypeList) {
        this.intentTypeList = intentTypeList == null ? new ArrayList<>() : new ArrayList<>(intentTypeList);
    }

    public DefaultResponse getDefaultResponse() {
        return defaultResponse;
    }

    public void setDefaultResponse(DefaultResponse defaultResponse) {
        this.defaultResponse = defaultResponse == null ? new DefaultResponse() : defaultResponse;
    }

    public boolean isSuppressCompletionSignal() {
        return suppressCompletionSignal;
    }

    public void setSuppressCompletionSignal(boolean suppressCompletionSignal) {
        this.suppressCompletionSignal = suppressCompletionSignal;
    }

    public Double getStreamFirstFrameTimeout() {
        return streamFirstFrameTimeout;
    }

    public void setStreamFirstFrameTimeout(Double streamFirstFrameTimeout) {
        this.streamFirstFrameTimeout = streamFirstFrameTimeout;
    }
}
