/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.AgentStateCollection;

/**
 * Owns a detached persisted snapshot so failure evidence cannot publish uncommitted agent state.
 *
 * @since 2026-10-29
 */
final class CheckpointSnapshotSession extends BaseSession {
    private final String sessionId;
    private final String agentId;
    private final AgentStateCollection state = new AgentStateCollection();

    CheckpointSnapshotSession(BaseSession source) {
        sessionId = source.sessionId();
        agentId = Checkpointer.agentId(source);
    }

    String agentId() {
        return agentId;
    }

    /** {@inheritDoc} */
    @Override
    public String sessionId() {
        return sessionId;
    }

    /** {@inheritDoc} */
    @Override
    public AgentStateCollection state() {
        return state;
    }
}
