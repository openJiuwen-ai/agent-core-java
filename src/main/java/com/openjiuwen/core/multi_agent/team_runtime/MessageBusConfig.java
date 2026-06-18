/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

/**
 * Message bus configuration.
 *
 * <p>Mirrors Python's {@code MessageBusConfig} in
 * {@code openjiuwen/core/multi_agent/team_runtime/message_bus.py}.</p>
 */
public class MessageBusConfig {

    private int maxQueueSize = 1000;
    private Double processTimeout = 1800.0;
    private String teamId;

    public MessageBusConfig() {
    }

    public MessageBusConfig(int maxQueueSize, Double processTimeout, String teamId) {
        this.maxQueueSize = maxQueueSize;
        this.processTimeout = processTimeout;
        this.teamId = teamId;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Double getProcessTimeout() {
        return processTimeout;
    }

    public void setProcessTimeout(Double processTimeout) {
        this.processTimeout = processTimeout;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
}
