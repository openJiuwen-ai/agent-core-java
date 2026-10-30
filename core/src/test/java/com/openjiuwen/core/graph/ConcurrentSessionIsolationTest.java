/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.UUID;

/**
 * Verifies that PregelGraph no longer overwrites the session field when
 * compile() is called a second time.
 *
 * <p>Previously PregelGraph stored a mutable {@code session} instance field
 * that compile() overwrote on every call. When the same graph was compiled
 * with session B while run A was still in flight, A's vertices and afterStep
 * closure silently switched to B's workflow state.
 *
 * <p>After the fix, the session is captured per-run in the CompiledGraph and
 * vertex init happens at invocation time, not at compile time. These tests
 * assert the fixed behavior.
 */
@Timeout(30)
class ConcurrentSessionIsolationTest {

    /**
     * After the fix, compile(B) must NOT overwrite the PregelGraph's session.
     *
     * <p>The PregelGraph no longer has a session instance field. The afterStep
     * closure captures the compile-argument session directly. We verify that
     * the PregelGraph has no mutable session field after the fix.
     */
    @Test
    void serialCompile_secondCompileMustNotOverwritePregelGraphSession() {
        PregelGraph graph = new PregelGraph();
        SimpleExecutable exec = new SimpleExecutable();
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        // First compile with session A
        graph.compile(sessionA, null);

        // Second compile with session B — must NOT store session on the graph
        graph.compile(sessionB, null);

        // The PregelGraph no longer has a session field after the fix.
        // Reflecting should fail (NoSuchFieldException) — proving the field
        // was removed. If the field still exists, it must NOT be sessionB.
        BaseSession graphSession = reflectSessionField(graph);
        if (graphSession != null) {
            assertThat(graphSession)
                    .as("PregelGraph.session must not be overwritten to sessionB by compile(B)")
                    .isNotSameAs(sessionB);
        }
    }

    /**
     * After the fix, vertex init happens at invocation time (in
     * CompiledGraph.invokeInternal), not at compile time.
     *
     * <p>compile(A) then compile(B) must NOT overwrite vertex.session, because
     * compile no longer calls node.init(). The vertex session is set during
     * invoke, not compile.
     */
    @Test
    void serialCompile_secondCompileMustNotOverwriteVertexSession() {
        PregelGraph graph = new PregelGraph();
        SimpleExecutable exec = new SimpleExecutable();
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        // First compile with session A — no vertex init at compile time
        graph.compile(sessionA, null);
        Vertex vertex = graph.getVertex("node");
        BaseSession vertexSessionAfterA = reflectVertexSessionField(vertex);

        // Vertex session should be null (not yet initialized) — init happens at invoke
        assertThat(vertexSessionAfterA)
                .as("after compile(A), vertex.session should be null — init happens at invoke, not compile")
                .isNull();

        // Second compile with session B — still no vertex init
        graph.compile(sessionB, null);
        BaseSession vertexSessionAfterB = reflectVertexSessionField(vertex);

        // Vertex session should still be null — compile(B) did not overwrite it
        assertThat(vertexSessionAfterB)
                .as("after compile(B), vertex.session should still be null — compile does not init vertices")
                .isNull();
    }

    /**
     * The compiled pregel is shared across compile calls (static compilation).
     */
    @Test
    void pregelIsSharedAcrossCompiles_provingStaticCompilation() {
        PregelGraph graph = new PregelGraph();
        SimpleExecutable exec = new SimpleExecutable();
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        graph.compile(sessionA, null);
        Object pregelAfterA = reflectPregelField(graph);

        graph.compile(sessionB, null);
        Object pregelAfterB = reflectPregelField(graph);

        assertThat(pregelAfterA)
                .as("pregel should be the same instance — confirming static compilation")
                .isSameAs(pregelAfterB);
    }

    // --- helpers ---

    private static WorkflowRuntimeSession createSession(String label) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        WorkflowRuntimeState state = WorkflowRuntimeState.create();
        return new WorkflowRuntimeSession(label, null, sessionId, state, null);
    }

    @SuppressWarnings("unchecked")
    private static BaseSession reflectSessionField(PregelGraph graph) {
        try {
            java.lang.reflect.Field field = PregelGraph.class.getDeclaredField("session");
            field.setAccessible(true);
            return (BaseSession) field.get(graph);
        } catch (NoSuchFieldException e) {
            // Field was removed in the fix — this is the expected state
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reflect PregelGraph.session", e);
        }
    }

    private static BaseSession reflectVertexSessionField(Vertex vertex) {
        try {
            java.lang.reflect.Field field = Vertex.class.getDeclaredField("session");
            field.setAccessible(true);
            return (BaseSession) field.get(vertex);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reflect Vertex.session", e);
        }
    }

    private static Object reflectPregelField(PregelGraph graph) {
        try {
            java.lang.reflect.Field field = PregelGraph.class.getDeclaredField("pregel");
            field.setAccessible(true);
            return field.get(graph);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reflect PregelGraph.pregel", e);
        }
    }

    /**
     * Minimal executable that returns an empty map.
     */
    private static final class SimpleExecutable
            extends Executable<Map<String, Object>, Map<String, Object>> {

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                             BaseSession session, Object... kwargs) {
            return Map.of();
        }
    }
}
