/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Map;

/**
 * Package-level multi-agent session alias.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.multi_agent.Session} export so
 * callers can stay within the {@code multiagent} package when working with
 * group sessions.
 */
public class Session extends AgentGroupSessionApi {

    public Session(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs);
    }

    public Session(String sessionId) {
        super(sessionId);
    }

    public Session() {
        super();
    }

    public static Session create(String sessionId, Map<String, Object> envs) {
        return new Session(sessionId, envs);
    }
}
