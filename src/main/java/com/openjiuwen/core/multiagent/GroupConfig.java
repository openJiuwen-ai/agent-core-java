/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

/**
 * Group Runtime Configuration.
 * <p>
 * Mutable runtime parameters for agent group execution.
 * Follows the same Card + Config pattern as ReActAgentConfig.
 * <p>
 * Mirrors Python's {@code GroupConfig} in {@code multi_agent/config.py}.
 * 
 * @since 0.1.7
 */
public class GroupConfig {
    private int maxAgents = 10;
    private int maxConcurrentMessages = 100;
    private double messageTimeout = 30.0;

    /**
     * GroupConfig.
     * 
     * @since 0.1.7
     */
    public GroupConfig() {
    }

    /**
     * getMaxAgents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxAgents() {
        return maxAgents;
    }

    /**
     * setMaxAgents.
     * 
     * @param maxAgents maxAgents
     * @since 0.1.7
     */
    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }

    /**
     * getMaxConcurrentMessages.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxConcurrentMessages() {
        return maxConcurrentMessages;
    }

    /**
     * setMaxConcurrentMessages.
     * 
     * @param maxConcurrentMessages maxConcurrentMessages
     * @since 0.1.7
     */
    public void setMaxConcurrentMessages(int maxConcurrentMessages) {
        this.maxConcurrentMessages = maxConcurrentMessages;
    }

    /**
     * getMessageTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getMessageTimeout() {
        return messageTimeout;
    }

    /**
     * setMessageTimeout.
     * 
     * @param messageTimeout messageTimeout
     * @since 0.1.7
     */
    public void setMessageTimeout(double messageTimeout) {
        this.messageTimeout = messageTimeout;
    }

    /**
     * Configure maximum agents (supports chaining).
     * 
     * @param maxAgents maximum number of agents
     * @return this config instance
     * @since 0.1.7
     */
    public GroupConfig configureMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
        return this;
    }

    /**
     * Configure message timeout (supports chaining).
     * 
     * @param timeout timeout in seconds
     * @return this config instance
     * @since 0.1.7
     */
    public GroupConfig configureTimeout(double timeout) {
        this.messageTimeout = timeout;
        return this;
    }

    /**
     * Configure concurrency limit (supports chaining).
     * 
     * @param maxConcurrent maximum concurrent messages
     * @return this config instance
     * @since 0.1.7
     */
    public GroupConfig configureConcurrency(int maxConcurrent) {
        this.maxConcurrentMessages = maxConcurrent;
        return this;
    }
}
