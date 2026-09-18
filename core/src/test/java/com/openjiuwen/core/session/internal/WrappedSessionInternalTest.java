/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for internal wrapped sessions.
 *
 * <p>Mirrors Python's {@code WrappedSession}, {@code StateSession}, and
 * {@code RouterSession} in
 * {@code openjiuwen/core/session/internal/wrapper.py}.</p>
 */
class WrappedSessionInternalTest {

    @Test
    void wrappedSessionDelegatesConfigAccessAndKeepsDefaultHooksNoOp() {
        TestSession inner = new TestSession();
        inner.config.addWorkflowConfig("workflow-a", "workflow-config");
        inner.config.setAgentConfig("agent-config");
        inner.config.setEnvs(Map.of("region", "cn"));
        TestStateSession wrapper = new TestStateSession(inner);

        assertEquals("workflow-config", wrapper.getWorkflowConfig("workflow-a"));
        assertEquals("agent-config", wrapper.getAgentConfig());
        assertEquals("cn", wrapper.getEnv("region"));
        assertSame(inner, wrapper.base());
        assertSame(inner, wrapper.innerSession());
        assertEquals("", wrapper.userId());

        wrapper.preRun(Map.of("inputs", "ignored"));
        wrapper.commit();
        wrapper.postRun();
        wrapper.release("session-a");
    }

    @Test
    void stateSessionDelegatesStateAndStreamWriters() {
        TestSession inner = new TestSession();
        TestStateSession wrapper = new TestStateSession(inner);

        wrapper.updateState(Map.of("local", "value"));
        wrapper.updateGlobalState(Map.of("global", "value"));
        wrapper.writeStream(Map.of("type", "message", "index", 1, "payload", "hello"));

        assertEquals("value", wrapper.getState("local"));
        assertEquals("value", wrapper.getGlobalState("global"));
        assertEquals("node-a", wrapper.executableId());
        assertEquals("session-a", wrapper.sessionId());
        assertSame(inner.config(), wrapper.config());
        assertSame(inner.state(), wrapper.state());
        assertSame(inner.streamWriterManager(), wrapper.streamWriterManager());

        Object emitted = inner.streamWriterManager.streamEmitter().getStreamQueue().receive(100);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("message", output.getType());
        assertEquals(1, output.getIndex());
        assertEquals("hello", output.getPayload());
    }

    @Test
    void routerSessionKeepsRoutingWritesAndConfigAccessAsNoOps() {
        TestSession inner = new TestSession();
        RouterSession router = new RouterSession(inner);

        router.updateState(Map.of("local", "ignored"));
        router.updateGlobalState(Map.of("global", "ignored"));
        router.writeStream(Map.of("type", "message", "index", 1, "payload", "ignored"));
        router.writeCustomStream(Map.of("payload", "ignored"));
        router.interact("ignored");
        router.trace(Map.of("event", "ignored"));
        router.traceError(new IllegalStateException("ignored"));

        assertNull(router.getWorkflowConfig("workflow-a"));
        assertNull(router.getAgentConfig());
        assertNull(router.getEnv("region"));
        assertNull(router.base());
        assertNull(router.streamWriter());
        assertNull(router.customWriter());
        assertNull(router.getState("local"));
        assertNull(router.getGlobalState("global"));
        assertNull(inner.streamWriterManager.streamEmitter().getStreamQueue().receive(100));
    }

    private static final class TestStateSession extends StateSession {
        private TestStateSession(BaseSession innerSession) {
            super(innerSession);
        }

        @Override
        public void trace(Map<String, Object> data) {
        }

        @Override
        public void traceError(Throwable error) {
        }

        @Override
        public void interact(Object value) {
        }
    }

    private static final class TestSession extends BaseSession {
        private final Config config = new Config();
        private final AgentStateCollection state = new AgentStateCollection();
        private final StreamWriterManager streamWriterManager = new StreamWriterManager(new StreamEmitter());

        @Override
        public Config config() {
            return config;
        }

        @Override
        public AgentStateCollection state() {
            return state;
        }

        @Override
        public StreamWriterManager streamWriterManager() {
            return streamWriterManager;
        }

        @Override
        public String sessionId() {
            return "session-a";
        }

        public String executableId() {
            return "node-a";
        }
    }
}
