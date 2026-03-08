/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the ContextEngine module.
 * Tests context creation, message management, and context windows.
 * Corresponds to Python's context_evolver quickstart pattern.
 */
@Tag("system-test")
class ContextEngineSystemTest {

    @Test
    @DisplayName("Create context and add messages")
    void testCreateContextAndAddMessages() {
        ContextEngine engine = new ContextEngine();

        List<BaseMessage> history = new ArrayList<>();
        history.add(new UserMessage("你好"));
        history.add(new AssistantMessage("你好！有什么可以帮助你的？"));

        ModelContext context = engine.createContext(
                "ctx_test_1", new MinimalSession(), null, history, null);

        assertNotNull(context, "Context should be created");
        System.out.println("[ContextEngine] Context created: " + context);
    }

    @Test
    @DisplayName("Create context with default parameters")
    void testCreateContextDefaults() {
        ContextEngine engine = new ContextEngine();
        ModelContext context = engine.createContext("ctx_default", new MinimalSession());
        assertNotNull(context);
        System.out.println("[ContextEngine Default] Context created");
    }

    @Test
    @DisplayName("Clear context by contextId and sessionId")
    void testClearContext() {
        ContextEngine engine = new ContextEngine();
        String sessionId = "sess_clear_test";
        Session session = new MinimalSession(sessionId);

        engine.createContext("ctx_clear_1", session);
        engine.createContext("ctx_clear_2", session);

        // Clear specific context
        engine.clearContext("ctx_clear_1", sessionId);

        // Clear all contexts for session
        engine.clearContext(null, sessionId);
        System.out.println("[ContextEngine Clear] Contexts cleared");
    }

    @Test
    @DisplayName("Create multiple contexts and retrieve them")
    void testMultipleContextsGet() {
        ContextEngine engine = new ContextEngine();
        String sessionId = "sess_multi";
        Session session = new MinimalSession(sessionId);

        engine.createContext("ctx_a", session);
        engine.createContext("ctx_b", session);

        ModelContext ctxA = engine.getContext("ctx_a", sessionId);
        ModelContext ctxB = engine.getContext("ctx_b", sessionId);

        assertNotNull(ctxA, "Context A should be retrievable");
        assertNotNull(ctxB, "Context B should be retrievable");
        System.out.println("[ContextEngine Multi] Retrieved ctx_a and ctx_b");
    }

    /**
     * Minimal Session for context engine testing.
     */
    static class MinimalSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private String currentOperatorId;

        MinimalSession() {
            this("test-session-" + System.currentTimeMillis());
        }

        MinimalSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> stateMap) {
            if (stateMap != null) state.putAll(stateMap);
        }

        @Override
        public void setCurrentOperatorId(String operatorId) {
            this.currentOperatorId = operatorId;
        }

        @Override
        public String getCurrentOperatorId() {
            return currentOperatorId;
        }
    }
}
