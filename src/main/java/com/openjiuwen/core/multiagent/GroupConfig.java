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
 */
public class GroupConfig {

    private int maxAgents = 10;
    private int maxConcurrentMessages = 100;
    private double messageTimeout = 30.0;

    /**
     * Auto-generated for codecheck compliance.
     */
    public GroupConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxAgents() {
        return maxAgents;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxConcurrentMessages() {
        return maxConcurrentMessages;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxConcurrentMessages(int maxConcurrentMessages) {
        this.maxConcurrentMessages = maxConcurrentMessages;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getMessageTimeout() {
        return messageTimeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMessageTimeout(double messageTimeout) {
        this.messageTimeout = messageTimeout;
    }

    /**
     * Configure maximum agents (supports chaining).
     *
     * @param maxAgents maximum number of agents
     * @return this config instance
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
     */
    public GroupConfig configureConcurrency(int maxConcurrent) {
        this.maxConcurrentMessages = maxConcurrent;
        return this;
    }
}
