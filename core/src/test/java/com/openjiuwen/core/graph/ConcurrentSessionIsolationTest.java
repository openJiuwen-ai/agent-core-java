/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that PregelGraph no longer overwrites the session field when
 * compile() is called a second time.
 *
 * <p>Previously PregelGraph stored a mutable {@code session} instance field
 * that compile() overwrote on every call. When the same graph was compiled
 * with session B while run A was still in flight, A's vertices and afterStep
 * closure silently switched to B's workflow state.
 *
 * <p>After the fix, every compile builds its own Pregel engine whose step
 * callback and checkpointer derive from that compile's session, and vertex
 * init happens at invocation time, not at compile time. These tests assert
 * the fixed behavior.
 */
@Timeout(30)
class ConcurrentSessionIsolationTest {

    /**
     * The shared session field must stay removed: compile(B) must NOT
     * overwrite any graph-level session state.
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

        // The session instance field was removed by the fix. Assert the
        // removal itself so re-introducing the field fails this test.
        assertThatThrownBy(() -> PregelGraph.class.getDeclaredField("session"))
                .as("PregelGraph.session must stay removed — re-adding the field regresses "
                        + "session isolation across compiles")
                .isInstanceOf(NoSuchFieldException.class);
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
     * Each compile must build its own Pregel engine so the step callback and
     * checkpointer are derived from that compile's session. Sharing one engine
     * across compiles would pin every later run to the first compile's
     * session closure.
     */
    @Test
    void serialCompile_eachCompileGetsOwnPregelEngine() {
        PregelGraph graph = new PregelGraph();
        SimpleExecutable exec = new SimpleExecutable();
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        CompiledGraph compiledA = (CompiledGraph) graph.compile(sessionA, null);
        CompiledGraph compiledB = (CompiledGraph) graph.compile(sessionB, null);

        assertThat(compiledA.getPregel())
                .as("each compile must build its own Pregel engine — a shared engine would "
                        + "bind later runs to the first compile's session closure")
                .isNotSameAs(compiledB.getPregel());
    }

    /**
     * The step callback of the second compile must commit to the second
     * session's workflow state, not the first one's. This is the serial
     * regression guard: the second run's per-step commits (outputs, node
     * states) must land in its own state.
     */
    @Test
    void serialCompile_secondRunCommitsToItsOwnWorkflowState() throws Exception {
        PregelGraph graph = new PregelGraph();
        CommittingExecutable exec = new CommittingExecutable();
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        CompiledGraph compiledA = (CompiledGraph) graph.compile(sessionA, null);
        compiledA.invoke(runInputs(), sessionA);

        CompiledGraph compiledB = (CompiledGraph) graph.compile(sessionB, null);
        compiledB.invoke(runInputs(), sessionB);

        assertThat(exec.sessionIds)
                .as("each run must observe its own invocation session")
                .containsExactly("session-A", "session-B");
        assertThat(sessionB.state().getOutputs("node"))
                .as("second run's outputs must commit into the second session's state")
                .isNotNull();
    }

    /**
     * Guard for the shared-vertex concurrency constraint: while one run is
     * still in flight on the graph, a second overlapping run must be refused
     * with PREGEL_GRAPH_CONCURRENT_RUN_INVALID (112104) instead of racing
     * over the shared vertex sessions. After the first run finishes, serial
     * reuse must work again.
     */
    @Test
    void concurrentInvoke_overlappingRunIsRefusedWithConcurrentRunError() throws Exception {
        PregelGraph graph = new PregelGraph();
        CountDownLatch insideNode = new CountDownLatch(1);
        CountDownLatch releaseNode = new CountDownLatch(1);
        BlockingExecutable exec = new BlockingExecutable(insideNode, releaseNode);
        graph.addNode("node", exec);
        graph.startNode("node");
        graph.endNode("node");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        CompiledGraph compiledA = (CompiledGraph) graph.compile(sessionA, null);
        CompiledGraph compiledB = (CompiledGraph) graph.compile(sessionB, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> runA = executor.submit(() -> compiledA.invoke(runInputs(), sessionA));
            // Wait until run A is executing inside the node, then start run B.
            assertThat(insideNode.await(10, TimeUnit.SECONDS))
                    .as("run A should reach the node body")
                    .isTrue();
            assertThatThrownBy(() -> compiledB.invoke(runInputs(), sessionB))
                    .as("an overlapping second run must be refused instead of racing "
                            + "over the shared vertex sessions")
                    .isInstanceOf(BaseError.class)
                    .satisfies(error -> assertThat(((BaseError) error).getCode())
                            .isEqualTo(112104));

            // Release run A; it must complete normally despite the refused run B.
            releaseNode.countDown();
            runA.get(20, TimeUnit.SECONDS);
            assertThat(exec.observedWorkflows)
                    .as("only run A may have reached a node — the refused run B must not")
                    .containsExactly("session-A");

            // Serial reuse after the in-flight run completes must still work.
            CompiledGraph compiledC = (CompiledGraph) graph.compile(sessionB, null);
            compiledC.invoke(runInputs(), sessionB);
            assertThat(exec.observedWorkflows).containsExactly("session-A", "session-B");
        } finally {
            releaseNode.countDown();
            executor.shutdownNow();
        }
    }

    // --- helpers ---

    private static Map<String, Object> runInputs() {
        return Map.of("inputs", Map.of(), "config", Map.of());
    }

    private static WorkflowRuntimeSession createSession(String label) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        WorkflowRuntimeState state = WorkflowRuntimeState.create();
        return new WorkflowRuntimeSession(label, null, sessionId, state, null);
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

    /**
     * Executable that blocks inside the node body until released, so the
     * test can hold a run in flight while starting an overlapping one.
     */
    private static final class BlockingExecutable
            extends Executable<Map<String, Object>, Map<String, Object>> {

        private final CountDownLatch insideNode;
        private final CountDownLatch release;
        private final List<String> observedWorkflows = new CopyOnWriteArrayList<>();

        private BlockingExecutable(CountDownLatch insideNode, CountDownLatch release) {
            this.insideNode = insideNode;
            this.release = release;
        }

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                             BaseSession session, Object... kwargs) {
            if (session instanceof WorkflowRuntimeSession runtimeSession) {
                observedWorkflows.add(runtimeSession.workflowId());
            }
            insideNode.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of("done", true);
        }
    }

    /**
     * Executable that writes an output so the commit path becomes observable
     * through the session's workflow state.
     */
    private static final class CommittingExecutable
            extends Executable<Map<String, Object>, Map<String, Object>> {

        private final List<String> sessionIds = new CopyOnWriteArrayList<>();

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                             BaseSession session, Object... kwargs) {
            if (session instanceof WorkflowRuntimeSession runtimeSession) {
                sessionIds.add(runtimeSession.workflowId());
                runtimeSession.state().setOutputs(Map.of("done", true));
            }
            return Map.of("done", true);
        }
    }
}
