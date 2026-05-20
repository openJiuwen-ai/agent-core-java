/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

/**
 * Legacy AgentGroup Configuration.
 * <p>
 * Mirrors Python's {@code AgentGroupConfig} in {@code multi_agent/legacy/config.py}.
 *
 * @deprecated Use {@link com.openjiuwen.core.multiagent.GroupConfig} with the new Card + Config pattern.
 */
@Deprecated
public class AgentGroupConfig {

    private final String groupId;
    private int maxAgents;
    private int maxConcurrentMessages;
    private double messageTimeout;

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupConfig(String groupId) {
        this(groupId, 10, 100, 30.0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout) {
        this.groupId = groupId;
        this.maxAgents = maxAgents;
        this.maxConcurrentMessages = maxConcurrentMessages;
        this.messageTimeout = messageTimeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getGroupId() {
        return groupId;
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
}
