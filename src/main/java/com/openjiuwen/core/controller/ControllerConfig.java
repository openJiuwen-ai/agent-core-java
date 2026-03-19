/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller;

import java.util.List;

/**
 * Controller configuration.
 * <p>
 * Defines configuration parameters for the controller, grouped into categories:
 * task scheduling, task management, event queue, and intent recognition.
 * <p>
 * Mirrors Python's {@code ControllerConfig(BaseModel)}.
 */
public class ControllerConfig {

    // ==================== Task scheduling configuration ====================

    /** Maximum number of concurrent tasks (0 means no limit). Default: 5 */
    private int maxConcurrentTasks = 5;

    /** Task scheduling interval in seconds. Default: 1.0 */
    private double scheduleInterval = 1.0;

    /** Task timeout in seconds. null means no timeout. */
    private Double taskTimeout;

    // ==================== Task management configuration ====================

    /** Default task priority. Larger numbers mean higher priority. Default: 1 */
    private int defaultTaskPriority = 1;

    /** Whether to enable task persistence. Default: false */
    private boolean enableTaskPersistence = false;

    // ==================== Event queue configuration ====================

    /** Event queue size. Default: 10000 */
    private int eventQueueSize = 10000;

    /** Event processing timeout in seconds. Default: 300 */
    private double eventTimeout = 300;

    // ==================== Intent recognition configuration ====================

    /** Whether to enable intent recognition. Default: false */
    private boolean enableIntentRecognition = false;

    /** Intent LLM model ID */
    private String intentLlmId = "";

    /** Confidence threshold for intent recognition. Default: 0.7 */
    private double intentConfidenceThreshold = 0.7;

    /** List of supported intent types */
    private List<String> intentTypeList = List.of(
            "create_task",
            "pause_task",
            "resume_task",
            "cancel_task",
            "unknown_task"
    );

    public ControllerConfig() {
    }

    // Builder pattern

    public static ControllerConfig defaultConfig() {
        return new ControllerConfig();
    }

    // Getters and setters

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
        if (taskTimeout != null && taskTimeout < 600) {
            throw new IllegalArgumentException("taskTimeout must be >= 600 or null");
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

    public int getEventQueueSize() {
        return eventQueueSize;
    }

    public void setEventQueueSize(int eventQueueSize) {
        if (eventQueueSize < 1) {
            throw new IllegalArgumentException("eventQueueSize must be >= 1");
        }
        this.eventQueueSize = eventQueueSize;
    }

    public double getEventTimeout() {
        return eventTimeout;
    }

    public void setEventTimeout(double eventTimeout) {
        if (eventTimeout < 100) {
            throw new IllegalArgumentException("eventTimeout must be >= 100");
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
        this.intentLlmId = intentLlmId;
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
        this.intentTypeList = intentTypeList;
    }
}
