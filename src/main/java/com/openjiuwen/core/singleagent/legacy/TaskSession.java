/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.session.AgentSessionApi;

import java.util.Collections;
import java.util.Iterator;
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
public class TaskSession implements AgentSessionApi {

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
     * Create a TaskSession with the given session ID using a minimal stub.
     *
     * @param sessionId the session identifier
     */
    public TaskSession(String sessionId) {
        LegacyApi.emitDeprecationWarning("TaskSession", "AgentSessionApi");
        this.inner = new StubSession(sessionId);
    }

    /**
     * Create a TaskSession with default session ID.
     */
    public TaskSession() {
        this("default");
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
        if (inner instanceof StubSession) {
            return;
        }
        try {
            inner.getClass().getMethod("postRun").invoke(inner);
        } catch (ReflectiveOperationException ignored) {
            // Not all AgentSessionApi implementations expose postRun.
        }
    }

    // ==================== AgentSessionApi interface delegation ====================

    @Override
    public String getSessionId() {
        return inner.getSessionId();
    }

    @Override
    public Object getState(String key) {
        return inner.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        inner.updateState(data);
    }

    @Override
    public void writeStream(Object data) {
        inner.writeStream(data);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return inner.streamIterator();
    }

    // ==================== Session interface delegation ====================

    /**
     * Minimal stub session for legacy constructors.
     */
    private static final class StubSession implements AgentSessionApi {
        private final String sessionId;

        StubSession(String sessionId) {
            this.sessionId = sessionId != null ? sessionId : "default";
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }
}
