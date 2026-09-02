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
 * @since 0.1.7
 */
@Deprecated
public class AgentGroupConfig {
    private final String groupId;
    private int maxAgents;
    private int maxConcurrentMessages;
    private double messageTimeout;

    /**
     * AgentGroupConfig.
     * 
     * @param groupId groupId
     * @since 0.1.7
     */
    public AgentGroupConfig(String groupId) {
        this(groupId, 10, 100, 30.0);
    }

    /**
     * AgentGroupConfig.
     * 
     * @param groupId groupId
     * @param maxAgents maxAgents
     * @param maxConcurrentMessages maxConcurrentMessages
     * @param messageTimeout messageTimeout
     * @since 0.1.7
     */
    public AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout) {
        this.groupId = groupId;
        this.maxAgents = maxAgents;
        this.maxConcurrentMessages = maxConcurrentMessages;
        this.messageTimeout = messageTimeout;
    }

    /**
     * getGroupId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getGroupId() {
        return groupId;
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
}
