/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;

import java.util.Map;

/**
 * Legacy session wrapper for backward compatibility.
 *
 * <p>Mirrors Python's {@code TaskSession(StateSession)} in
 * {@code single_agent/legacy/react_agent.py}.</p>
 *
 * <p>Wraps an {@link AgentSessionApi} and exposes the subset of
 * methods that legacy code expects (session id, state, post-run).</p>
 *
 * @deprecated Use {@link AgentSessionApi} directly instead.
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public class TaskSession implements Session {

    private final AgentSessionApi inner;

    /**
     * Create a TaskSession wrapping the given agent session.
     *
     * @param inner the underlying {@link AgentSessionApi}
     */
    public TaskSession(AgentSessionApi inner) {
        LegacyApi.emitDeprecationWarning("TaskSession", "AgentSessionApi");
        this.inner = inner;
    }

    /**
     * Create a TaskSession with the given session ID.
     *
     * @param sessionId the session identifier
     */
    public TaskSession(String sessionId) {
        this(new AgentSessionApi(sessionId));
    }

    /**
     * Create a TaskSession with default session ID.
     */
    public TaskSession() {
        this(new AgentSessionApi());
    }

    /**
     * Get the underlying {@link AgentSessionApi}.
     *
     * @return the inner session
     */
    public AgentSessionApi getInnerSession() {
        return inner;
    }

    /**
     * Run post-execution hooks (mirrors Python's {@code post_run}).
     */
    public void postRun() {
        inner.postRun();
    }

    // ==================== Session interface delegation ====================

    @Override
    public String getSessionId() {
        return inner.getSessionId();
    }

    @Override
    public Object getState(String key) {
        return inner.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> state) {
        inner.updateState(state);
    }
}
