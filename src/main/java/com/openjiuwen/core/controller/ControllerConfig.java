/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.util.List;

/**
 * Controller configuration.
 * <p>
 * Defines configuration parameters for the controller, grouped into categories:
 * task scheduling, task management, event queue, and intent recognition.
 * <p>
 * Mirrors Python's {@code ControllerConfig(BaseModel)}.
 * 
 * @since 0.1.7
 */
public class ControllerConfig {
    // ==================== Task scheduling configuration ====================
    /**
     * Maximum number of concurrent tasks.
     * <p>On JDK 17 this caps the in-flight task count to protect platform threads, and the
     * default follows {@link OpenJiuwenExecutors#defaultTaskConcurrency()} ({@code max(64, CPU×8)})
     * so the admission gate stays aligned with the underlying thread-pool capacity; on JDK 21+
     * the cap is skipped because virtual threads carry negligible creation cost and concurrency
     * is admission-controlled by the runtime layer (e.g. {@code TaskAdmissionGate}).</p>
     */
    private int maxConcurrentTasks = OpenJiuwenExecutors.defaultTaskConcurrency();

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

    /**
     * List of supported intent types
     * 
     * @since 0.1.7
     */
    private List<String> intentTypeList =
        List.of("create_task", "pause_task", "resume_task", "cancel_task", "unknown_task");

    /**
     * ControllerConfig.
     * 
     * @since 0.1.7
     */
    public ControllerConfig() {
    }

    // Builder pattern

    /**
     * Creates a default ControllerConfig instance.
     * 
     * @return a new ControllerConfig with default values
     * @since 0.1.7
     */
    public static ControllerConfig defaultConfig() {
        return new ControllerConfig();
    }

    // Getters and setters

    /**
     * Gets the maximum number of concurrent tasks.
     * 
     * @return the max concurrent tasks value
     * @since 0.1.7
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * Sets the maximum number of concurrent tasks.
     * 
     * @param maxConcurrentTasks the max concurrent tasks value
     * @since 0.1.7
     */
    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    /**
     * Gets the task scheduling interval.
     * 
     * @return the schedule interval in seconds
     * @since 0.1.7
     */
    public double getScheduleInterval() {
        return scheduleInterval;
    }

    /**
     * Sets the task scheduling interval.
     * 
     * @param scheduleInterval the schedule interval in seconds (must be >= 0.1)
     * @since 0.1.7
     */
    public void setScheduleInterval(double scheduleInterval) {
        if (scheduleInterval < 0.1) {
            throw new IllegalArgumentException("scheduleInterval must be >= 0.1");
        }
        this.scheduleInterval = scheduleInterval;
    }

    /**
     * getTaskTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getTaskTimeout() {
        return taskTimeout;
    }

    /**
     * setTaskTimeout.
     * 
     * @param taskTimeout taskTimeout
     * @since 0.1.7
     */
    public void setTaskTimeout(Double taskTimeout) {
        if (taskTimeout != null && taskTimeout < 600) {
            throw new IllegalArgumentException("taskTimeout must be >= 600 or null");
        }
        this.taskTimeout = taskTimeout;
    }

    /**
     * getDefaultTaskPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getDefaultTaskPriority() {
        return defaultTaskPriority;
    }

    /**
     * setDefaultTaskPriority.
     * 
     * @param defaultTaskPriority defaultTaskPriority
     * @since 0.1.7
     */
    public void setDefaultTaskPriority(int defaultTaskPriority) {
        this.defaultTaskPriority = defaultTaskPriority;
    }

    /**
     * isEnableTaskPersistence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableTaskPersistence() {
        return enableTaskPersistence;
    }

    /**
     * setEnableTaskPersistence.
     * 
     * @param enableTaskPersistence enableTaskPersistence
     * @since 0.1.7
     */
    public void setEnableTaskPersistence(boolean enableTaskPersistence) {
        this.enableTaskPersistence = enableTaskPersistence;
    }

    /**
     * getEventQueueSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEventQueueSize() {
        return eventQueueSize;
    }

    /**
     * setEventQueueSize.
     * 
     * @param eventQueueSize eventQueueSize
     * @since 0.1.7
     */
    public void setEventQueueSize(int eventQueueSize) {
        if (eventQueueSize < 1) {
            throw new IllegalArgumentException("eventQueueSize must be >= 1");
        }
        this.eventQueueSize = eventQueueSize;
    }

    /**
     * getEventTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getEventTimeout() {
        return eventTimeout;
    }

    /**
     * setEventTimeout.
     * 
     * @param eventTimeout eventTimeout
     * @since 0.1.7
     */
    public void setEventTimeout(double eventTimeout) {
        if (eventTimeout < 100) {
            throw new IllegalArgumentException("eventTimeout must be >= 100");
        }
        this.eventTimeout = eventTimeout;
    }

    /**
     * isEnableIntentRecognition.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableIntentRecognition() {
        return enableIntentRecognition;
    }

    /**
     * setEnableIntentRecognition.
     * 
     * @param enableIntentRecognition enableIntentRecognition
     * @since 0.1.7
     */
    public void setEnableIntentRecognition(boolean enableIntentRecognition) {
        this.enableIntentRecognition = enableIntentRecognition;
    }

    /**
     * getIntentLlmId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getIntentLlmId() {
        return intentLlmId;
    }

    /**
     * setIntentLlmId.
     * 
     * @param intentLlmId intentLlmId
     * @since 0.1.7
     */
    public void setIntentLlmId(String intentLlmId) {
        this.intentLlmId = intentLlmId;
    }

    /**
     * getIntentConfidenceThreshold.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getIntentConfidenceThreshold() {
        return intentConfidenceThreshold;
    }

    /**
     * setIntentConfidenceThreshold.
     * 
     * @param intentConfidenceThreshold intentConfidenceThreshold
     * @since 0.1.7
     */
    public void setIntentConfidenceThreshold(double intentConfidenceThreshold) {
        if (intentConfidenceThreshold < 0.0 || intentConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException("intentConfidenceThreshold must be between 0.0 and 1.0");
        }
        this.intentConfidenceThreshold = intentConfidenceThreshold;
    }

    /**
     * getIntentTypeList.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getIntentTypeList() {
        return intentTypeList;
    }

    /**
     * setIntentTypeList.
     * 
     * @param intentTypeList intentTypeList
     * @since 0.1.7
     */
    public void setIntentTypeList(List<String> intentTypeList) {
        this.intentTypeList = intentTypeList;
    }
}
