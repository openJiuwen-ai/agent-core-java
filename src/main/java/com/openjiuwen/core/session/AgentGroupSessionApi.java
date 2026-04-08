/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Map;

/**
 * User-facing agent group session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.agent_group.Session}.
 * <p>
 * Extends {@link AgentSessionApi} so legacy multi-agent sessions inherit the
 * same state, streaming, and interaction helpers exposed by Python's
 * {@code AgentGroupSession(AgentSession)} implementation.
 */
public class AgentGroupSessionApi extends AgentSessionApi {

    /**
     * Create a new agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     */
    public AgentGroupSessionApi(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs, null);
    }

    public AgentGroupSessionApi(String sessionId) {
        this(sessionId, null);
    }

    public AgentGroupSessionApi() {
        this(null, null);
    }

    /**
     * Factory method to create an agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     * @return a new AgentGroupSessionApi
     */
    public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSessionApi(sessionId, envs);
    }
}
