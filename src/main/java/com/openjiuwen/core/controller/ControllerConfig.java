// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller;

/**
 * Controller configuration.
 *
 * <p>Defines configuration parameters for the controller and controls its behavior.
 * The configuration items are grouped into several categories: task scheduling,
 * task management, event queue, and intent recognition.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class ControllerConfig {

    // ==================== Task scheduling configuration ====================

    /**
     * Maximum number of concurrent tasks.
     * Controls the upper limit of tasks running at the same time. 0 means no limit.
     */
    private int maxConcurrentTasks = 5;

    /**
     * Task scheduling interval in seconds.
     * The scheduler periodically scans pending tasks using this interval.
     * Must be >= 0.1.
     */
    private double scheduleInterval = 1.0;

    /**
     * Task timeout in seconds.
     * Tasks that exceed this duration are marked as failed.
     * null means no timeout. If set, must be >= 600.
     * Package-private to allow tests to bypass validation (matching Python's direct attribute access).
     */
    Double taskTimeout = null;

    // ==================== Task management configuration ====================

    /**
     * Default task priority. Used when a task is created without an explicit priority.
     * Larger numbers mean higher priority.
     */
    private int defaultTaskPriority = 1;

    /**
     * Whether to enable task persistence.
     * When enabled, task states are stored for recovery.
     */
    private boolean enableTaskPersistence = false;

    // ==================== Event queue configuration ====================

    /**
     * Event queue size. Limits the number of events that can be stored in the queue.
     * Must be >= 1 if set.
     */
    private Integer eventQueueSize = 10000;

    /**
     * Event processing timeout in milliseconds.
     * Events that are not processed within this time are discarded.
     * Must be >= 600 if set.
     */
    private Double eventTimeout = 120000.0;

    /**
     * LLM ID for intent recognition.
     */
    private String intentLlmId = "";

    // ==================== Intent recognition configuration ====================

    /**
     * Confidence threshold for intent recognition.
     * Intents below this value are treated as UNKNOWN_TASK. Range 0.0-1.0.
     */
    private double intentConfidenceThreshold = 0.7;

    /**
     * Default constructor with all default values.
     */
    public ControllerConfig() {
    }

    // Getters and Setters

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
            throw new IllegalArgumentException("taskTimeout must be >= 600 if set");
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
            throw new IllegalArgumentException("eventQueueSize must be >= 1 if set");
        }
        this.eventQueueSize = eventQueueSize;
    }

    public Double getEventTimeout() {
        return eventTimeout;
    }

    public void setEventTimeout(Double eventTimeout) {
        if (eventTimeout != null && eventTimeout < 600) {
            throw new IllegalArgumentException("eventTimeout must be >= 600 if set");
        }
        this.eventTimeout = eventTimeout;
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

    /**
     * Creates a new Builder for ControllerConfig.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ControllerConfig.
     */
    public static class Builder {
        private int maxConcurrentTasks = 5;
        private double scheduleInterval = 1.0;
        private Double taskTimeout = null;
        private int defaultTaskPriority = 1;
        private boolean enableTaskPersistence = false;
        private Integer eventQueueSize = 10000;
        private Double eventTimeout = 120000.0;
        private String intentLlmId = "";
        private double intentConfidenceThreshold = 0.7;

        public Builder maxConcurrentTasks(int maxConcurrentTasks) {
            this.maxConcurrentTasks = maxConcurrentTasks;
            return this;
        }

        public Builder scheduleInterval(double scheduleInterval) {
            this.scheduleInterval = scheduleInterval;
            return this;
        }

        public Builder taskTimeout(Double taskTimeout) {
            this.taskTimeout = taskTimeout;
            return this;
        }

        public Builder defaultTaskPriority(int defaultTaskPriority) {
            this.defaultTaskPriority = defaultTaskPriority;
            return this;
        }

        public Builder enableTaskPersistence(boolean enableTaskPersistence) {
            this.enableTaskPersistence = enableTaskPersistence;
            return this;
        }

        public Builder eventQueueSize(Integer eventQueueSize) {
            this.eventQueueSize = eventQueueSize;
            return this;
        }

        public Builder eventTimeout(Double eventTimeout) {
            this.eventTimeout = eventTimeout;
            return this;
        }

        public Builder intentLlmId(String intentLlmId) {
            this.intentLlmId = intentLlmId;
            return this;
        }

        public Builder intentConfidenceThreshold(double intentConfidenceThreshold) {
            this.intentConfidenceThreshold = intentConfidenceThreshold;
            return this;
        }

        /**
         * Builds the ControllerConfig.
         *
         * @return the configured ControllerConfig
         */
        public ControllerConfig build() {
            ControllerConfig config = new ControllerConfig();
            config.setMaxConcurrentTasks(maxConcurrentTasks);
            config.setScheduleInterval(scheduleInterval);
            // Directly set field to bypass validation (matching Python builder behavior)
            config.taskTimeout = taskTimeout;
            config.setDefaultTaskPriority(defaultTaskPriority);
            config.setEnableTaskPersistence(enableTaskPersistence);
            config.setEventQueueSize(eventQueueSize);
            config.setEventTimeout(eventTimeout);
            config.setIntentLlmId(intentLlmId);
            config.setIntentConfidenceThreshold(intentConfidenceThreshold);
            return config;
        }
    }
}

