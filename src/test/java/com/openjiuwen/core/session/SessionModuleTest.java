/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the root session module.
 *
 * <p>Mirrors Python's {@code BaseSession}, {@code ProxySession}, and deprecated
 * {@code Session} in {@code openjiuwen/core/session/session.py}.</p>
 */
class SessionModuleTest {

    @Test
    void proxySessionDelegatesOnlyPythonOverriddenMethods() {
        TestSession stub = new TestSession();
        ProxySession proxy = new ProxySession(stub);

        assertSame(stub.config(), proxy.config());
        assertSame(stub.state(), proxy.state());
        assertSame(stub.tracer(), proxy.tracer());
        assertSame(stub.streamWriterManager(), proxy.streamWriterManager());
        assertEquals("session-a", proxy.sessionId());
        assertSame(stub.checkpointer(), proxy.checkpointer());
        assertSame(stub, proxy.getSession());

        assertNull(proxy.actorManager());
        proxy.close();
        assertEquals(0, stub.closeCount);
    }

    @Test
    void proxySessionCanRebindBackingSession() {
        TestSession first = new TestSession("first");
        TestSession second = new TestSession("second");
        ProxySession proxy = new ProxySession(first);

        assertEquals("first", proxy.sessionId());
        proxy.setSession(second);

        assertEquals("second", proxy.sessionId());
        assertSame(second, proxy.getSession());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSessionExposesPythonDeprecationMessage() {
        Session session = new Session();

        assertTrue(session.deprecationMessage().contains("openjiuwen.core.session.Session"));
        assertTrue(session.deprecationMessage().contains("openjiuwen.core.[module].Session"));
    }

    private static final class TestSession extends BaseSession {
        private final Config config = new Config();
        private final AgentStateCollection state = new AgentStateCollection();
        private final StreamWriterManager streamWriterManager = new StreamWriterManager(new StreamEmitter());
        private final Object tracer = new Object();
        private final Object checkpointer = new Object();
        private final String sessionId;
        private int closeCount;

        private TestSession() {
            this("session-a");
        }

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public AgentStateCollection state() {
            return state;
        }

        @Override
        public Object tracer() {
            return tracer;
        }

        @Override
        public StreamWriterManager streamWriterManager() {
            return streamWriterManager;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public Object checkpointer() {
            return checkpointer;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
