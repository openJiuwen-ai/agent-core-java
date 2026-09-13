/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.openjiuwen.core.session.WorkflowSessionApi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * Integration tests for the Session module.
 * Tests Session creation, state management, and lifecycle.
 * Corresponds to Session management patterns used across all Python examples.
 */
@Tag("system-test")
class SessionSystemTest {
    @Test
    @DisplayName("WorkflowSessionApi creation with session ID")
    void testWorkflowSessionCreation() {
        String sessionId = UUID.randomUUID().toString();
        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());

        assertNotNull(session);
        assertEquals(sessionId, session.getSessionId());
        assertNotNull(session.getCallbackManager());
        System.out.println("[Session Create] SessionId: " + session.getSessionId());
    }

    @Test
    @DisplayName("WorkflowSessionApi auto-generated session ID")
    void testWorkflowSessionAutoId() {
        WorkflowSessionApi session = new WorkflowSessionApi(null, null, null);
        assertNotNull(session.getSessionId(), "Session ID should be auto-generated");
        System.out.println("[Session AutoId] SessionId: " + session.getSessionId());
    }

    @Test
    @DisplayName("WorkflowSessionApi with string-only constructor")
    void testWorkflowSessionStringConstructor() {
        String sessionId = "test-session-123";
        WorkflowSessionApi session = new WorkflowSessionApi(sessionId);
        assertEquals(sessionId, session.getSessionId());
    }

    @Test
    @DisplayName("Multiple sessions have independent IDs")
    void testMultipleSessions() {
        WorkflowSessionApi s1 = new WorkflowSessionApi(null, null, null);
        WorkflowSessionApi s2 = new WorkflowSessionApi(null, null, null);

        assertNotNull(s1.getSessionId());
        assertNotNull(s2.getSessionId());
        assertNotNull(s1.getSessionId());
        // IDs should be different (UUID-based)
        assertEquals(false, s1.getSessionId().equals(s2.getSessionId()),
                "Different sessions should have different IDs");
        System.out.println("[Session Multi] s1=" + s1.getSessionId() + ", s2=" + s2.getSessionId());
    }

    @Test
    @DisplayName("WorkflowSessionApi with environment variables")
    void testWorkflowSessionWithEnvs() {
        Map<String, Object> envs = Map.of("DEBUG", "true", "MAX_RETRIES", 3);
        WorkflowSessionApi session = new WorkflowSessionApi(null, "env-session", envs);

        assertEquals("env-session", session.getSessionId());
        assertNotNull(session.getEnvs());
        assertEquals("true", session.getEnvs().get("DEBUG"));
        assertEquals(3, session.getEnvs().get("MAX_RETRIES"));
        System.out.println("[Session Env] Envs: " + session.getEnvs());
    }
}
