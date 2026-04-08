/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    public AgentGroupSession(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs);
    }

    public AgentGroupSession(String sessionId) {
        super(sessionId);
    }

    public AgentGroupSession() {
        super();
    }

    public static AgentGroupSession create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSession(sessionId, envs);
    }
}
