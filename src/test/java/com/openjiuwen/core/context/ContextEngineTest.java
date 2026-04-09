/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.SimpleTokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextEngine}.
 */
class ContextEngineTest {

    private ContextEngine engine;
    private Session testSession;

    @BeforeEach
    void setUp() {
        engine = new ContextEngine(ContextEngineConfig.builder()
                .maxContextMessageNum(50)
                .build());

        testSession = new Session() {
            private final Map<String, Object> state = new HashMap<>();

            @Override
            public String getSessionId() {
                return "test_session_id";
            }

            @Override
            public Object getState(String key) {
                return state.get(key);
            }

            @Override
            public void updateState(Map<String, Object> stateUpdate) {
                state.putAll(stateUpdate);
            }
        };
    }

    @Test
    @DisplayName("createContext creates new context")
    void testCreateContext() {
        ModelContext ctx = engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        assertNotNull(ctx);
        assertEquals("test_session_id", ctx.sessionId());
        assertEquals("ctx1", ctx.contextId());
    }

    @Test
    @DisplayName("createContext returns cached context on second call")
    void testCreateContextCached() {
        ModelContext ctx1 = engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        ModelContext ctx2 = engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        assertSame(ctx1, ctx2);
    }

    @Test
    @DisplayName("createContext with null session uses default session id")
    void testCreateContextNullSession() {
        ModelContext ctx = engine.createContext("ctx1", null,
                null, null, new SimpleTokenCounter());
        assertNotNull(ctx);
        assertEquals("default_session_id", ctx.sessionId());
    }

    @Test
    @DisplayName("createContext with history messages seeds context")
    void testCreateContextWithHistory() {
        ModelContext ctx = engine.createContext("ctx1", testSession,
                null,
                List.of(new UserMessage("past_msg")),
                new SimpleTokenCounter());
        assertEquals(1, ctx.size());
    }

    @Test
    @DisplayName("getContext returns existing context")
    void testGetContext() {
        engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());

        ModelContext retrieved = engine.getContext("ctx1", "test_session_id");
        assertNotNull(retrieved);
    }

    @Test
    @DisplayName("getContext returns null for non-existent context")
    void testGetContextMissing() {
        assertNull(engine.getContext("nonexistent", "test_session_id"));
    }

    @Test
    @DisplayName("clearContext removes specific context")
    void testClearContextSpecific() {
        engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        engine.clearContext("ctx1", "test_session_id");

        assertNull(engine.getContext("ctx1", "test_session_id"));
    }

    @Test
    @DisplayName("clearContext with null sessionId clears all")
    void testClearContextAll() {
        engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        engine.createContext("ctx2", testSession,
                null, null, new SimpleTokenCounter());

        engine.clearContext(null, null);

        assertNull(engine.getContext("ctx1", "test_session_id"));
        assertNull(engine.getContext("ctx2", "test_session_id"));
    }

    @Test
    @DisplayName("clearContext with sessionId clears all for that session")
    void testClearContextBySession() {
        engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());

        engine.clearContext(null, "test_session_id");

        assertNull(engine.getContext("ctx1", "test_session_id"));
    }

    @Test
    @DisplayName("saveContexts persists state to session")
    void testSaveContexts() {
        ModelContext ctx = engine.createContext("ctx1", testSession,
                null, null, new SimpleTokenCounter());
        ctx.addMessages(List.of(new UserMessage("test")));

        engine.saveContexts(testSession, null);

        Object state = testSession.getState("context");
        assertNotNull(state);
    }

    @Test
    @DisplayName("context_id dots are replaced with underscores")
    void testContextIdDotReplacement() {
        ModelContext ctx = engine.createContext("my.ctx.id", testSession,
                null, null, new SimpleTokenCounter());
        assertEquals("my_ctx_id", ctx.contextId());
    }

    @Test
    @DisplayName("registerProcessor and createProcessor work together")
    void testProcessorRegistration() {
        // Register a simple no-op processor
        ContextEngine.registerProcessor("NoOpProcessor", NoOpProcessor.class,
                config -> new NoOpProcessor());

        ModelContext ctx = engine.createContext("ctx_proc", testSession,
                List.of(new ContextEngine.ProcessorSpec("NoOpProcessor", new Object())),
                null, new SimpleTokenCounter());
        assertNotNull(ctx);
    }

    /**
     * Simple no-op processor for testing registration.
     */
    private static class NoOpProcessor extends com.openjiuwen.core.context.processor.ContextProcessor {
        NoOpProcessor() {
            super(null);
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }
}
