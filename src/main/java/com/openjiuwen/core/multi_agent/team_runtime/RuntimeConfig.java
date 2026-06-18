/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

/**
 * Multi-agent runtime configuration.
 *
 * <p>Mirrors Python's {@code RuntimeConfig} in
 * {@code openjiuwen/core/multi_agent/team_runtime/team_runtime.py}.</p>
 */
public class RuntimeConfig {

    private String teamId = "default";
    private MessageBusConfig messageBus;
    private double p2pTimeout = 1800.0;

    public RuntimeConfig() {
    }

    public RuntimeConfig(String teamId, MessageBusConfig messageBus, double p2pTimeout) {
        this.teamId = teamId == null ? "default" : teamId;
        this.messageBus = messageBus;
        this.p2pTimeout = p2pTimeout;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId == null ? "default" : teamId;
    }

    public MessageBusConfig getMessageBus() {
        return messageBus;
    }

    public void setMessageBus(MessageBusConfig messageBus) {
        this.messageBus = messageBus;
    }

    public double getP2pTimeout() {
        return p2pTimeout;
    }

    public void setP2pTimeout(double p2pTimeout) {
        this.p2pTimeout = p2pTimeout;
    }
}
