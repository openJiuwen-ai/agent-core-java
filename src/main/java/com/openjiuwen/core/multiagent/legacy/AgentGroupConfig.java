/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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

    public AgentGroupConfig(String groupId) {
        this(groupId, 10, 100, 30.0);
    }

    public AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout) {
        this.groupId = groupId;
        this.maxAgents = maxAgents;
        this.maxConcurrentMessages = maxConcurrentMessages;
        this.messageTimeout = messageTimeout;
    }

    public String getGroupId() {
        return groupId;
    }

    public int getMaxAgents() {
        return maxAgents;
    }

    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }

    public int getMaxConcurrentMessages() {
        return maxConcurrentMessages;
    }

    public void setMaxConcurrentMessages(int maxConcurrentMessages) {
        this.maxConcurrentMessages = maxConcurrentMessages;
    }

    public double getMessageTimeout() {
        return messageTimeout;
    }

    public void setMessageTimeout(double messageTimeout) {
        this.messageTimeout = messageTimeout;
    }
}
