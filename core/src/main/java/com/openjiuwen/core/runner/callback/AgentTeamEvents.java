/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code AgentTeamEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class AgentTeamEvents {
    public static final String AGENT_P2P_RECEIVED = Events.getEvent("agent_p2p_received");
    public static final String AGENT_PUBSUB_RECEIVED = Events.getEvent("agent_pubsub_received");

    private AgentTeamEvents() {
    }
}
