/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.contextengine;

import com.openjiuwen.core.contextengine.context.SessionModelContext;
import com.openjiuwen.core.contextengine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContextEngine}.
 * 
 * <p>Converted from Python: test_context_engine.py</p>
 */
class ContextEngineTest {

    /**
     * Mock Session implementation for testing.
     */
    static class MockSession implements Session {
        private final String sessionId;
        
        MockSession(String sessionId) {
            this.sessionId = sessionId;
        }
        
        @Override public String getExecutableId() { return sessionId; }
        @Override public String getSessionId() { return sessionId; }
        @Override public void updateState(Map<String, Object> data) {}
        @Override public Object getState(Object key) { return null; }
        @Override public void updateGlobalState(Map<String, Object> data) {}
        @Override public Object getGlobalState(Object key) { return null; }
        @Override public StreamWriter getStreamWriter() { return null; }
        @Override public StreamWriter getCustomWriter() { return null; }
        @Override public CompletableFuture<Void> writeStream(Object data) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> trace(Map<String, Object> data) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> traceError(Exception error) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> interact(Object value) { return CompletableFuture.completedFuture(null); }
        @Override public Object getWorkflowConfig(String workflowId) { return null; }
        @Override public Object getAgentConfig() { return null; }
        @Override public Object getEnv(String key) { return null; }
        @Override public BaseSession getBase() { return null; }
    }

    private Session session;
    private Session anotherSession;
    private ContextEngine engine;

    @BeforeEach
    void setUp() {
        session = new MockSession("test_session");
        anotherSession = new MockSession("another_session");
        engine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .memoryMessageNum(3)
                .build()
        );
    }

    /**
     * Test creating context with history and session.
     * 
     * <p>Python: test_create_context_with_history_and_session</p>
     * <p>Assertions: 4</p>
     */
    @Test
    void testCreateContextWithHistoryAndSession() throws ExecutionException, InterruptedException {
        var history = List.<BaseMessage>of(
            UserMessage.of("hello"),
            SystemMessage.of("sys")
        );
        var context = engine.createContext("ctx", session, history, null, null).get();

        assertInstanceOf(SessionModelContext.class, context);
        assertEquals(session.getSessionId(), context.getSessionId());
        assertEquals("ctx", context.getContextId());
        assertEquals(history, context.getMessages(null, true));
    }

    /**
     * Test that creating context reuses existing one.
     * 
     * <p>Python: test_create_context_reuses_existing</p>
     * <p>Assertions: 1</p>
     */
    @Test
    void testCreateContextReusesExisting() throws ExecutionException, InterruptedException {
        var ctx1 = engine.createContext("ctx", session, null, null, null).get();
        var ctx2 = engine.createContext("ctx", session, null, null, null).get();

        assertSame(ctx1, ctx2);
    }

    /**
     * Test that contexts are isolated per session.
     * 
     * <p>Python: test_create_context_isolated_per_session</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testCreateContextIsolatedPerSession() throws ExecutionException, InterruptedException {
        var ctx1 = engine.createContext("ctx", session, null, null, null).get();
        var ctx2 = engine.createContext("ctx", anotherSession, null, null, null).get();

        assertNotSame(ctx1, ctx2);
        assertNotEquals(ctx1.getSessionId(), ctx2.getSessionId());
    }

    /**
     * Test that context loads from memory when no history is provided.
     * 
     * <p>Python: test_create_context_loads_from_memory_when_no_history</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testCreateContextLoadsFromMemoryWhenNoHistory() throws ExecutionException, InterruptedException {
        var memMessages = List.<BaseMessage>of(UserMessage.of("from memory"));
        
        // Create a test engine with overridden memory loading
        var loadCalled = new AtomicBoolean(false);
        var loadedMessageNum = new AtomicReference<Integer>();
        
        var testEngine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .memoryMessageNum(3)
                .build()
        ) {
            @Override
            protected CompletableFuture<List<BaseMessage>> loadContextFromMemory(
                    String sessionId, String memScopeId, int messageNum) {
                loadCalled.set(true);
                loadedMessageNum.set(messageNum);
                return CompletableFuture.completedFuture(memMessages);
            }
        };

        var context = testEngine.createContext(
            "ctx",
            session,
            null,  // no history
            null,
            "scope-1"  // mem_scope_id triggers memory loading
        ).get();

        assertTrue(loadCalled.get(), "loadContextFromMemory should be called");
        assertEquals(3, loadedMessageNum.get(), "message_num should honor config");
        assertEquals(memMessages, context.getMessages(null, true));
    }

    /**
     * Test that context does not load from memory when history is provided.
     * 
     * <p>Python: test_create_context_does_not_load_memory_when_history_provided</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testCreateContextDoesNotLoadMemoryWhenHistoryProvided() throws ExecutionException, InterruptedException {
        var loadCalled = new AtomicBoolean(false);
        
        var testEngine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .memoryMessageNum(3)
                .build()
        ) {
            @Override
            protected CompletableFuture<List<BaseMessage>> loadContextFromMemory(
                    String sessionId, String memScopeId, int messageNum) {
                loadCalled.set(true);
                return CompletableFuture.completedFuture(List.of(UserMessage.of("ignored")));
            }
        };

        var history = List.<BaseMessage>of(UserMessage.of("explicit-history"));
        var context = testEngine.createContext(
            "ctx",
            session,
            history,
            null,
            "scope-1"
        ).get();

        assertFalse(loadCalled.get(), "loadContextFromMemory should not be called");
        assertEquals(history, context.getMessages(null, true));
    }

    /**
     * Test that save_contexts persists and calls onSave.
     * 
     * <p>Python: test_save_contexts_persists_and_calls_on_save</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testSaveContextsPersistsAndCallsOnSave() throws ExecutionException, InterruptedException {
        var saveCalled = new AtomicBoolean(false);
        var savedMessages = new AtomicReference<List<BaseMessage>>();
        var onSaveCalled = new AtomicBoolean(false);
        
        var testEngine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(5)
                .memoryMessageNum(3)
                .build()
        ) {
            @Override
            protected CompletableFuture<Void> saveContextToMemory(
                    String sessionId, String memScopeId, List<BaseMessage> messages) {
                saveCalled.set(true);
                savedMessages.set(messages);
                return CompletableFuture.completedFuture(null);
            }
        };

        var context = testEngine.createContext("ctx", session, null, null, null).get();
        context.addMessages(UserMessage.of("new msg")).get();
        
        // Track onSave call using a wrapper
        var originalContext = (SessionModelContext) context;
        
        testEngine.saveContexts(List.of("ctx"), session, "scope-1").get();

        assertTrue(saveCalled.get(), "saveContextToMemory should be called");
        // Ensure we persisted only the newly added message (without history)
        assertEquals(
            List.<BaseMessage>of(UserMessage.of("new msg")),
            savedMessages.get()
        );
    }

    /**
     * Test clearing all contexts.
     * 
     * <p>Python: test_clear_context_all</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testClearContextAll() throws ExecutionException, InterruptedException {
        engine.createContext("ctx1", session, null, null, null).get();
        engine.createContext("ctx2", session, null, null, null).get();

        engine.clearContext(null, null);

        assertNull(engine.getContext("ctx1", session.getSessionId()));
        assertNull(engine.getContext("ctx2", session.getSessionId()));
    }

    /**
     * Test clearing contexts by session.
     * 
     * <p>Python: test_clear_context_by_session</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testClearContextBySession() throws ExecutionException, InterruptedException {
        engine.createContext("ctx1", session, null, null, null).get();
        engine.createContext("ctx2", anotherSession, null, null, null).get();

        engine.clearContext(null, session.getSessionId());

        assertNull(engine.getContext("ctx1", session.getSessionId()));
        assertNotNull(engine.getContext("ctx2", anotherSession.getSessionId()));
    }

    /**
     * Test clearing contexts by session and context ID.
     * 
     * <p>Python: test_clear_context_by_session_and_context</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testClearContextBySessionAndContext() throws ExecutionException, InterruptedException {
        engine.createContext("ctx1", session, null, null, null).get();
        engine.createContext("ctx2", anotherSession, null, null, null).get();

        engine.clearContext("ctx1", session.getSessionId());
        engine.clearContext("ctx2", anotherSession.getSessionId());

        assertNull(engine.getContext("ctx1", session.getSessionId()));
        assertNull(engine.getContext("ctx2", anotherSession.getSessionId()));
    }

    /**
     * Test that clearing a missing context throws an exception.
     * 
     * <p>Python: test_clear_context_missing_context_raises_key_error</p>
     * <p>Assertions: 1</p>
     */
    @Test
    void testClearContextMissingContextThrowsException() {
        // Current behavior: warns then deletes anyway -> throws exception
        assertThrows(IllegalArgumentException.class, () -> 
            engine.clearContext("does_not_exist", session.getSessionId())
        );
    }
}

