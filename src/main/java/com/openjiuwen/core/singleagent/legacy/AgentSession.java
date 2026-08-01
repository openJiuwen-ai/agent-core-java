/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

import java.util.Map;

/**
 * Legacy agent session factory.
 * <p>
 * Mirrors Python's {@code AgentSession} in {@code single_agent/legacy/react_agent.py}.
 * </p>
 * 
 * @deprecated Use {@link AgentSessionApi} directly instead.
 * @since 0.1.7
 */
@Deprecated
public class AgentSession {
    private final Checkpointer checkpointer;

    /**
     * AgentSession.
     * 
     * @since 0.1.7
     */
    public AgentSession() {
        this.checkpointer = CheckpointerFactory.getCheckpointer();
    }

    /**
     * Create and prepare a session for execution.
     * 
     * @param sessionId the session ID
     * @param inputs input data for pre-run
     * @return a prepared AgentSessionApi
     * @since 0.1.7
     */
    public AgentSessionApi preRun(String sessionId, Map<String, Object> inputs) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default_session";
        }
        com.openjiuwen.core.session.AgentSession session =
                com.openjiuwen.core.session.AgentSession.createAgentSession(sessionId, null, null);
        session.preRun(inputs);
        return session;
    }

    /**
     * Release session resources.
     * 
     * @param sessionId the session ID to release
     * @since 0.1.7
     */
    public void release(String sessionId) {
        if (checkpointer != null) {
            checkpointer.release(sessionId);
        }
    }
}
