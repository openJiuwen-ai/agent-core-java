/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.SessionConfigAccess;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for the public workflow-session facade.
 *
 * <p>Mirrors Python's {@code Session} and {@code create_workflow_session} in
 * {@code openjiuwen/core/session/workflow.py}.</p>
 */
class WorkflowSessionApiTest {

    @Test
    void noParentUsesExplicitSessionIdAndOriginalEnvsReference() {
        Map<String, Object> envs = Map.of("region", "cn");

        WorkflowSessionApi session = new WorkflowSessionApi(null, "workflow-session", envs);

        assertEquals("workflow-session", session.getSessionId());
        assertSame(envs, session.getEnvs());
        assertNull(session.getParent());
    }

    @Test
    void noParentGeneratesUuidAndKeepsNullEnvs() {
        WorkflowSessionApi session = new WorkflowSessionApi();

        assertDoesNotThrow(() -> UUID.fromString(session.getSessionId()));
        assertNull(session.getEnvs());
        assertNull(session.getParent());
    }

    @Test
    void parentSessionKeepsProvidedSessionIdAndUsesParentEnvs() {
        Map<String, Object> parentEnvs = Map.of("from", "parent");
        ParentSession parent = new ParentSession(parentEnvs);

        WorkflowSessionApi session = new WorkflowSessionApi(parent, null, Map.of("from", "child"));

        assertNull(session.getSessionId());
        assertSame(parent, session.getParent());
        assertEquals(parentEnvs, session.getEnvs());
    }

    @Test
    void workflowCardSetterAndGetterPreserveObjectReference() {
        WorkflowSessionApi session = new WorkflowSessionApi();
        Object card = new Object();

        session.setWorkflowCard(card);

        assertSame(card, session.getWorkflowCard());
    }

    @Test
    void factoryReturnsWorkflowSessionWithSameConstructorSemantics() {
        Map<String, Object> envs = Map.of("mode", "test");

        WorkflowSessionApi session = WorkflowSessionApi.createWorkflowSession(null, "factory-session", envs);

        assertEquals("factory-session", session.getSessionId());
        assertSame(envs, session.getEnvs());
        assertNull(session.getParent());
    }

    /**
     * Mirrors Python's parent {@code BaseSession} collaborator consumed by
     * {@code Session.__init__} in {@code openjiuwen/core/session/workflow.py}.
     */
    private static final class ParentSession extends BaseSession {
        private final SessionConfigAccess config;

        private ParentSession(Map<String, Object> envs) {
            this.config = new SessionConfigAccess() {
                @Override
                public Object getEnv(String key) {
                    return envs.get(key);
                }

                @Override
                public Map<String, Object> getEnvs() {
                    return envs;
                }
            };
        }

        @Override
        public SessionConfigAccess config() {
            return config;
        }
    }
}
