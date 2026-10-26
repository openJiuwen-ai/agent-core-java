/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;

/**
 * Minimal agent-flavoured {@link BaseSession} stub shared by checkpointer tests that
 * only exercise session registration and eviction.
 */
final class InMemoryCheckpointerTestAgentSession extends BaseSession {
    private final String sessionId;
    private final AgentStateCollection state;

    InMemoryCheckpointerTestAgentSession(String sessionId) {
        this.sessionId = sessionId;
        this.state = new AgentStateCollection();
    }

    @Override
    public SessionConfigAccess config() {
        return new Config();
    }

    @Override
    public SessionStateAccess state() {
        return state;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }
}
