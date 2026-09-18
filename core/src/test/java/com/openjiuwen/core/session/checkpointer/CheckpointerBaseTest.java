/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.BaseSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests base checkpointer helpers and contracts.
 *
 * <p>Mirrors Python's {@code Checkpointer} and key helpers in
 * {@code openjiuwen/core/session/checkpointer/base.py}.</p>
 */
class CheckpointerBaseTest {

    @Test
    void getThreadIdJoinsSessionAndWorkflowId() {
        BaseSession session = new TestSession("session-a", "workflow-a");

        assertEquals("session-a:workflow-a", Checkpointer.getThreadId(session));
    }

    @Test
    void buildKeyMatchesPythonJoinSemantics() {
        assertEquals("a:b", Checkpointer.buildKey("a", "b"));
        assertEquals("a::b", Checkpointer.buildKey("a", "", "b"));
        assertThrows(NullPointerException.class, () -> Checkpointer.buildKey("a", null, "b"));
    }

    @Test
    void buildKeyWithNamespaceIncludesEntityAndSuffixes() {
        assertEquals(
                "sid:agent:agent-a:state",
                Checkpointer.buildKeyWithNamespace("sid", Checkpointer.SESSION_NAMESPACE_AGENT, "agent-a", "state")
        );
        assertEquals(
                "sid:workflow-graph:workflow-a",
                Checkpointer.buildKeyWithNamespace("sid", Checkpointer.WORKFLOW_NAMESPACE_GRAPH, "workflow-a")
        );
    }

    private static final class TestSession extends BaseSession {
        private final String sessionId;
        private final String workflowId;

        private TestSession(String sessionId, String workflowId) {
            this.sessionId = sessionId;
            this.workflowId = workflowId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        public String workflowId() {
            return workflowId;
        }
    }
}
