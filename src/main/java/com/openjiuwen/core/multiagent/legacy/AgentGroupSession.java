/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Map;

/**
 * Legacy package-level agent group session alias.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.multi_agent.legacy.AgentGroupSession}
 * export while keeping the shared Java implementation in
 * {@link AgentGroupSessionApi}.
 * 
 * @deprecated Use {@link com.openjiuwen.core.multiagent.Session}.
 * @since 0.1.7
 */
@Deprecated
public class AgentGroupSession extends AgentGroupSessionApi {
    /**
     * AgentGroupSession.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @since 0.1.7
     */
    public AgentGroupSession(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs);
    }

    /**
     * AgentGroupSession.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public AgentGroupSession(String sessionId) {
        super(sessionId);
    }

    /**
     * AgentGroupSession.
     * 
     * @since 0.1.7
     */
    public AgentGroupSession() {
        super();
    }

    /**
     * create.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static AgentGroupSession create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSession(sessionId, envs);
    }
}
