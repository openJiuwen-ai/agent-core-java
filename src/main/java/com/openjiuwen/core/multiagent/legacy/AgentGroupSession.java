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
 */
@Deprecated
public class AgentGroupSession extends AgentGroupSessionApi {

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSession(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSession(String sessionId) {
        super(sessionId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSession() {
        super();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AgentGroupSession create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSession(sessionId, envs);
    }
}
