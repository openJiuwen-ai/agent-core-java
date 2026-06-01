/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressor;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.SimpleTokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
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
 * <p>
 * Mirrors Python's {@code test_context_engine.py} in
 * {@code tests.unit_tests.core.context_engine.test_context_engine}.
 */
class ContextEngineTest {

    private ContextEngine engine;
    private Session testSession;

    @BeforeEach
    void setUp() {
        engine = new ContextEngine(ContextEngineConfig.builder()
                .maxContextMessageNum(50)
                .build());

        testSession = session("test_session_id");
    }

    private Session session(String sessionId) {
        return session(sessionId, new HashMap<>());
    }

    private Session session(String sessionId, Map<String, Object> state) {
        return new Session() {
            @Override
            public String getSessionId() {
                return sessionId;
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
    @DisplayName("createContext with history and session mirrors Python")
    void testCreateContextWithHistoryAndSession() {
        List<BaseMessage> history = List.of(new UserMessage("hello"), new SystemMessage("sys"));

        ModelContext context = engine.createContext("ctx", testSession,
                null, history, new SimpleTokenCounter());

        assertInstanceOf(com.openjiuwen.core.context.context.SessionModelContext.class, context);
        assertEquals(testSession.getSessionId(), context.sessionId());
        assertEquals("ctx", context.contextId());
        assertEquals(history, context.getMessages());
    }

    @Test
    @DisplayName("createContext reuses existing context")
    void testCreateContextReusesExisting() {
        ModelContext ctx1 = engine.createContext("ctx", testSession);
        ModelContext ctx2 = engine.createContext("ctx", testSession);

        assertSame(ctx1, ctx2);
    }

    @Test
    @DisplayName("createContext isolates contexts per session")
    void testCreateContextIsolatedPerSession() {
        Session anotherSession = session("another_session");

        ModelContext ctx1 = engine.createContext("ctx", testSession);
        ModelContext ctx2 = engine.createContext("ctx", anotherSession);

        assertNotSame(ctx1, ctx2);
        assertNotEquals(ctx1.sessionId(), ctx2.sessionId());
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
    @DisplayName("clearContext by session and context removes exact targets")
    void testClearContextBySessionAndContext() {
        Session anotherSession = session("another_session");
        engine.createContext("ctx1", testSession);
        engine.createContext("ctx2", anotherSession);

        engine.clearContext("ctx1", testSession.getSessionId());
        engine.clearContext("ctx2", anotherSession.getSessionId());

        assertNull(engine.getContext("ctx1", testSession.getSessionId()));
        assertNull(engine.getContext("ctx2", anotherSession.getSessionId()));
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
    @DisplayName("context save and load restores messages")
    void testContextSaveAndLoad() {
        Map<String, Object> sharedState = new HashMap<>();
        Session first = session("persisted_session", sharedState);
        Session second = session("persisted_session", sharedState);
        ContextEngine firstEngine = new ContextEngine(ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .build());
        ModelContext original = firstEngine.createContext("test_context", first);
        List<BaseMessage> messages = List.of(
                new SystemMessage("1"),
                new UserMessage("2"),
                new AssistantMessage("3"),
                new ToolMessage("4", "tool-call-1"));
        original.addMessages(messages);

        firstEngine.saveContexts(first, List.of("test_context"));
        ModelContext restored = new ContextEngine(ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .build()).createContext("test_context", second);

        assertEquals(original.getMessages(), restored.getMessages());
    }

    @Test
    @DisplayName("context save and load works with dotted context id")
    void testContextSaveAndLoadWithInvalidContextId() {
        Map<String, Object> sharedState = new HashMap<>();
        Session first = session("persisted_session", sharedState);
        Session second = session("persisted_session", sharedState);
        ContextEngine firstEngine = new ContextEngine(ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .build());
        ModelContext original = firstEngine.createContext("test.context.0.0.1", first);
        original.addMessages(List.of(new UserMessage("2"), new AssistantMessage("3")));

        firstEngine.saveContexts(first, null);
        ModelContext restored = new ContextEngine(ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .build()).createContext("test.context.0.0.1", second);

        assertEquals("test_context_0_0_1", restored.contextId());
        assertEquals(original.getMessages(), restored.getMessages());
    }

    @Test
    @DisplayName("createContext with null session uses default session id and can be retrieved")
    void testCreateContextWithSessionNoneUsesDefaultSessionId() {
        ModelContext context = engine.createContext("ctx", null);

        assertEquals("default_session_id", context.sessionId());
        assertEquals("ctx", context.contextId());
        assertSame(context, engine.getContext("ctx", "default_session_id"));
    }

    @Test
    @DisplayName("createContext accepts empty history messages")
    void testCreateContextEmptyHistoryMessages() {
        ModelContext context = engine.createContext("ctx", testSession,
                null, List.of(), new SimpleTokenCounter());

        assertEquals(List.of(), context.getMessages());
    }

    @Test
    @DisplayName("createContext with null history creates empty context")
    void testCreateContextHistoryMessagesNoneCreatesEmpty() {
        ModelContext context = engine.createContext("ctx", testSession);

        assertEquals(List.of(), context.getMessages());
    }

    @Test
    @DisplayName("context_id dots are replaced with underscores")
    void testContextIdDotReplacement() {
        ModelContext ctx = engine.createContext("my.ctx.id", testSession,
                null, null, new SimpleTokenCounter());
        assertEquals("my_ctx_id", ctx.contextId());
    }

    @Test
    @DisplayName("context id dots are replaced and retrievable")
    void testContextIdDotsReplacedByUnderscores() {
        ModelContext context = engine.createContext("a.b.c", testSession);

        assertEquals("a_b_c", context.contextId());
        assertSame(context, engine.getContext("a.b.c", testSession.getSessionId()));
    }

    @Test
    @DisplayName("null context id uses default context id")
    void testCreateContextDefaultContextId() {
        ModelContext context = engine.createContext(null, testSession);

        assertEquals("default_context_id", context.contextId());
        assertSame(context, engine.getContext("default_context_id", testSession.getSessionId()));
    }

    @Test
    @DisplayName("createContext keeps custom token counter")
    void testCreateContextWithCustomTokenCounter() {
        SimpleTokenCounter tokenCounter = new SimpleTokenCounter();

        ModelContext context = engine.createContext("ctx", testSession,
                null, null, tokenCounter);

        assertSame(tokenCounter, context.tokenCounter());
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

    @Test
    @DisplayName("createContext with registered processor builds processor")
    void testCreateContextWithRegisteredProcessor() {
        ContextEngine.registerProcessor("NoOpProcessor2", NoOpProcessor.class,
                config -> new NoOpProcessor());

        ModelContext context = engine.createContext("ctx_proc2", testSession,
                List.of(new ContextEngine.ProcessorSpec("NoOpProcessor2", new Object())),
                null, new SimpleTokenCounter());

        assertNotNull(context);
        assertDoesNotThrow(() -> context.addMessages(new UserMessage("hello")));
    }

    @Test
    @DisplayName("unknown processor type raises BaseError")
    void testCreateContextUnknownProcessorTypeRaises() {
        BaseError error = assertThrows(BaseError.class, () ->
                engine.createContext("ctx", testSession,
                        List.of(new ContextEngine.ProcessorSpec("UnknownProcessorType", new Object())),
                        null, new SimpleTokenCounter()));

        assertEquals(com.openjiuwen.core.common.exception.StatusCode.CONTEXT_EXECUTION_ERROR.getCode(),
                error.getCode());
    }

    @Test
    @DisplayName("processor init failure raises BaseError")
    void testCreateContextProcessorInitFailsRaises() {
        ContextEngine.registerProcessor("FailingProcessor", NoOpProcessor.class, config -> {
            throw new IllegalStateException("boom");
        });

        BaseError error = assertThrows(BaseError.class, () ->
                engine.createContext("ctx", testSession,
                        List.of(new ContextEngine.ProcessorSpec("FailingProcessor", new Object())),
                        null, new SimpleTokenCounter()));

        assertEquals(com.openjiuwen.core.common.exception.StatusCode.CONTEXT_EXECUTION_ERROR.getCode(),
                error.getCode());
    }

    @Test
    @DisplayName("getContext returns null when context does not exist")
    void testGetContextReturnsNoneWhenNotExists() {
        assertNull(engine.getContext("nonexistent", testSession.getSessionId()));
    }

    @Test
    @DisplayName("getContext accepts dotted context id")
    void testGetContextWithDottedContextId() {
        engine.createContext("x.y", testSession);

        ModelContext context = engine.getContext("x.y", testSession.getSessionId());

        assertNotNull(context);
        assertEquals("x_y", context.contextId());
    }

    @Test
    @DisplayName("getContext default params use default identifiers")
    void testGetContextDefaultParams() {
        ModelContext context = engine.createContext(null, null);

        assertSame(context, engine.getContext("default_context_id"));
    }

    @Test
    @DisplayName("clearContext by empty session is a no-op")
    void testClearContextBySessionWhenSessionHasNoContexts() {
        assertDoesNotThrow(() -> engine.clearContext(null, testSession.getSessionId()));
        assertNull(engine.getContext("any", testSession.getSessionId()));
    }

    @Test
    @DisplayName("clearContext for missing context is a no-op")
    void testClearContextBySessionAndContextWhenContextNotExists() {
        assertDoesNotThrow(() -> engine.clearContext("nonexistent", testSession.getSessionId()));
        assertNull(engine.getContext("nonexistent", testSession.getSessionId()));
    }

    @Test
    @DisplayName("clearContext all empties contexts across sessions")
    void testClearContextAllThenPoolEmpty() {
        Session anotherSession = session("another_session");
        engine.createContext("c1", testSession);
        engine.createContext("c2", anotherSession);

        engine.clearContext();

        assertNull(engine.getContext("c1", testSession.getSessionId()));
        assertNull(engine.getContext("c2", anotherSession.getSessionId()));
    }

    @Test
    @DisplayName("saveContexts with null session does not raise")
    void testSaveContextsSessionNoneDoesNotRaise() {
        assertDoesNotThrow(() -> engine.saveContexts(null, null));
    }

    @Test
    @DisplayName("saveContexts skips missing context ids")
    void testSaveContextsPartialContextIdsMissingSkipped() {
        engine.createContext("exists", testSession);

        assertDoesNotThrow(() -> engine.saveContexts(testSession, List.of("exists", "missing")));

        assertNotNull(testSession.getState("context"));
    }

    @Test
    @DisplayName("saveContexts with null context ids saves all contexts for session")
    void testSaveContextsContextIdsNoneSavesAllForSession() {
        engine.createContext("c1", testSession);
        engine.createContext("c2", testSession);

        engine.saveContexts(testSession, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) testSession.getState("context");
        assertTrue(states.containsKey("c1"));
        assertTrue(states.containsKey("c2"));
    }

    @Test
    @DisplayName("null engine config uses default config")
    void testEngineConfigNoneUsesDefault() {
        ContextEngine defaultEngine = new ContextEngine(null);

        assertNotNull(defaultEngine.createContext("ctx", null));
    }

    @Test
    @DisplayName("custom engine config is reflected in context window")
    void testEngineCustomConfigReflectedInContext() {
        ContextEngine customEngine = new ContextEngine(ContextEngineConfig.builder()
                .defaultWindowMessageNum(2)
                .build());
        ModelContext context = customEngine.createContext("ctx", testSession);
        context.addMessages(List.of(new UserMessage("1"), new AssistantMessage("2"), new UserMessage("3")));

        ContextWindow window = context.getContextWindow();

        assertEquals(2, window.getContextMessages().size());
    }

    @Test
    @DisplayName("registerProcessor registers processor class in map")
    void testRegisterProcessorRegistersInMap() {
        assertSame(CurrentRoundCompressor.class,
                ContextEngine.getProcessorClass("CurrentRoundCompressor"));
    }

    @Test
    @DisplayName("multiple sessions and contexts are isolated")
    void testMultipleSessionsAndContextsIsolated() {
        Session anotherSession = session("another_session");

        ModelContext c1 = engine.createContext("ctx_a", testSession);
        ModelContext c2 = engine.createContext("ctx_b", testSession);
        ModelContext c3 = engine.createContext("ctx_a", anotherSession);

        assertNotSame(c1, c2);
        assertNotSame(c1, c3);
        assertNotSame(c2, c3);
        assertSame(c1, engine.getContext("ctx_a", testSession.getSessionId()));
        assertSame(c2, engine.getContext("ctx_b", testSession.getSessionId()));
        assertSame(c3, engine.getContext("ctx_a", anotherSession.getSessionId()));
    }

    @Test
    @DisplayName("save context preserves loaded history unless explicit history overrides")
    void testSaveContext001() {
        Map<String, Object> sharedState = new HashMap<>();
        Session session = session("save-context-001", sharedState);
        ContextEngine firstEngine = new ContextEngine();
        List<BaseMessage> history = List.of(new SystemMessage("smart home assistant"));
        ModelContext context = firstEngine.createContext("ctx", session,
                null, history, new SimpleTokenCounter());
        List<BaseMessage> messages = List.of(
                new UserMessage("open curtains at 6 tomorrow"),
                new ToolMessage("scheduled curtains", "call-1"),
                new AssistantMessage("done"),
                new UserMessage("time"),
                new AssistantMessage("2026-01-01 18:15:24"));
        context.addMessages(messages);
        firstEngine.saveContexts(session, List.of("ctx"));

        ModelContext loaded = new ContextEngine().createContext("ctx", session);
        assertEquals(context.getMessages(), loaded.getMessages());

        ModelContext overridden = new ContextEngine().createContext("ctx", session,
                null, List.of(new SystemMessage("1")), new SimpleTokenCounter());
        assertEquals(List.of(new SystemMessage("1")), overridden.getMessages());
    }

    @Test
    @DisplayName("getContextWindow combines system, history, and context messages")
    void testGetContextWindow() {
        List<BaseMessage> history = List.of(
                new UserMessage("history_1"),
                new ToolMessage("history_2", "call-history"),
                new AssistantMessage("history_3"));
        ModelContext context = engine.createContext("ctx", testSession,
                null, history, new SimpleTokenCounter());
        List<BaseMessage> messages = List.of(new UserMessage("message 1"), new AssistantMessage("message 2"));
        context.addMessages(messages);
        List<BaseMessage> systemMessages = List.of(
                new SystemMessage("system 1"),
                new SystemMessage("system 2"),
                new SystemMessage("system 3"));

        ContextWindow full = context.getContextWindow(systemMessages, null, null, null);
        assertEquals(8, full.getMessages().size());

        ContextWindow limited = context.getContextWindow(systemMessages, null, 2, null);
        assertEquals(systemMessages.subList(1, 3), limited.getSystemMessages());
        assertEquals(List.of(), limited.getContextMessages());
    }

    @Test
    @DisplayName("save context preserves pop and set message history behavior")
    void testSaveContext007() {
        Map<String, Object> sharedState = new HashMap<>();
        Session session = session("save-context-007", sharedState);
        ContextEngine firstEngine = new ContextEngine();
        List<BaseMessage> history = List.of(new SystemMessage("smart home assistant"));
        ModelContext context = firstEngine.createContext("ctx", session,
                null, history, new SimpleTokenCounter());
        List<BaseMessage> messages = List.of(
                new UserMessage("open curtains"),
                new ToolMessage("scheduled curtains", "call-1"),
                new AssistantMessage("done"),
                new UserMessage("time"),
                new AssistantMessage("now"));
        context.addMessages(messages);
        firstEngine.saveContexts(session, List.of("ctx"));

        ModelContext loaded = new ContextEngine().createContext("ctx", session);
        assertEquals(List.of(), loaded.getMessages(null, false));
        assertEquals(history.size() + messages.size(), loaded.getMessages().size());

        assertEquals(messages.subList(messages.size() - 1, messages.size()), loaded.popMessages(1, true));
        assertEquals(history.size() + messages.size() - 1, loaded.getMessages().size());

        List<BaseMessage> newMessages = List.of(new UserMessage("message 1"), new AssistantMessage("message 2"));
        loaded.setMessages(newMessages, false);
        assertEquals(newMessages, loaded.getMessages(null, false));
    }

    /**
     * Simple no-op processor for testing registration.
     */
    private static class NoOpProcessor extends com.openjiuwen.core.context.processor.ContextProcessor {
        private Map<String, Object> loadedState = Map.of();

        NoOpProcessor() {
            super(null);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            loadedState = state == null ? Map.of() : new HashMap<>(state);
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("loaded", loadedState);
        }
    }
}
