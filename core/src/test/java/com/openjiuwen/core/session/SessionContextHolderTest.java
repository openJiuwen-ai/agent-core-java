/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the thread-local current session holder.
 */
class SessionContextHolderTest {

    @AfterEach
    void clearCurrentSession() {
        SessionContextHolder.clearCurrentSession();
    }

    @Test
    void currentSessionSetClearAndTypedLookup() {
        assertNull(SessionContextHolder.getCurrentSession());
        BaseSession session = new SimpleSession("session-1");

        SessionContextHolder.setCurrentSession(session);

        assertSame(session, SessionContextHolder.getCurrentSession());
        assertSame(session, SessionContextHolder.getCurrentSession(BaseSession.class));

        SessionContextHolder.setCurrentSession(null);

        assertNull(SessionContextHolder.getCurrentSession());
    }

    @Test
    void restoreCurrentSessionReplacesHolder() {
        BaseSession outer = new SimpleSession("outer");
        BaseSession inner = new SimpleSession("inner");
        SessionContextHolder.setCurrentSession(outer);

        Object previous = SessionContextHolder.getCurrentSession();
        SessionContextHolder.setCurrentSession(inner);
        assertSame(inner, SessionContextHolder.getCurrentSession());

        SessionContextHolder.restoreCurrentSession(previous);
        assertSame(outer, SessionContextHolder.getCurrentSession());
    }

    @Test
    void resolveSessionIdReadsKnownSessionTypes() {
        AgentSession agentSession = new AgentSession("agent-session", null, null);
        WorkflowSession workflowSession = new WorkflowSession(null, "workflow-session", Map.of());
        BaseSession baseSession = new SimpleSession("base-session");

        assertEquals("agent-session", SessionContextHolder.resolveSessionId(agentSession));
        assertEquals("workflow-session", SessionContextHolder.resolveSessionId(workflowSession));
        assertEquals("base-session", SessionContextHolder.resolveSessionId(baseSession));
        assertNull(SessionContextHolder.resolveSessionId(new Object()));
        assertNull(SessionContextHolder.resolveSessionId(null));
    }

    @Test
    void agentSessionFacadeRemainsUsable() {
        AgentSession session = new AgentSession("export-session", null, null);

        assertEquals("export-session", session.getSessionId());
        session.updateState(Map.of("ready", true));
        assertEquals(true, session.getState("ready"));
    }

    private static final class SimpleSession extends BaseSession {
        private final String sessionId;

        private SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }
    }
}
