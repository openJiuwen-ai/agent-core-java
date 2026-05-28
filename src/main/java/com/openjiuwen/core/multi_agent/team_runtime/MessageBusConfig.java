/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

/**
 * Configuration for message bus.
 * <p>
 * Mirrors Python's {@code MessageBusConfig} class from
 * <code>multi_agent/team_runtime/message_bus.py</code>.
 */
public class MessageBusConfig {

    private int maxQueueSize = 1000;
    private double processTimeout = 1800.0;
    private String teamId = null;

    public MessageBusConfig() {
    }

    public MessageBusConfig(int maxQueueSize, double processTimeout, String teamId) {
        this.maxQueueSize = maxQueueSize;
        this.processTimeout = processTimeout;
        this.teamId = teamId;
    }

    // Getters and setters
    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
    
    public double getProcessTimeout() { return processTimeout; }
    public void setProcessTimeout(double processTimeout) { this.processTimeout = processTimeout; }
    
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
}