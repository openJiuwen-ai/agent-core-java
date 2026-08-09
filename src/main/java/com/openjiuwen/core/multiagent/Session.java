/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.session.AgentGroupSession;

import java.util.Map;

/**
 * Package-level multi-agent session alias.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.multi_agent.Session} export so
 * callers can stay within the {@code multiagent} package when working with
 * group sessions.
 * 
 * @since 0.1.7
 */
public class Session extends AgentGroupSession {
    /**
     * Session.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @since 0.1.7
     */
    public Session(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs);
    }

    /**
     * Session.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public Session(String sessionId) {
        super(sessionId);
    }

    /**
     * Session.
     * 
     * @since 0.1.7
     */
    public Session() {
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
    public static Session create(String sessionId, Map<String, Object> envs) {
        return new Session(sessionId, envs);
    }
}
