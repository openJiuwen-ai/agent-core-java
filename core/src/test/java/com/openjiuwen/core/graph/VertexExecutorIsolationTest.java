/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Verifies that blocking workflow nodes do not occupy the shared CPU worker pool.
 *
 * @since 2026-10-28
 */
@Timeout(20)
class VertexExecutorIsolationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void call_withBlockedNode_allowsUnrelatedNodeToComplete(boolean shouldUseNullExecutor) throws Exception {
        ProbeExecutable blocked = new ProbeExecutable(1);
        Vertex waiting = createVertex("waiting", blocked, shouldUseNullExecutor);
        try (PausedCall paused = new PausedCall(blocked, waiting.call(Map.of()).toCompletableFuture())) {
            assertThat(blocked.started.get(5, TimeUnit.SECONDS)).isNotInstanceOf(ForkJoinWorkerThread.class);
            ProbeExecutable independent = new ProbeExecutable(0);
            Vertex ready = createVertex("ready", independent, shouldUseNullExecutor);
            ready.call(Map.of()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(independent.started.get(5, TimeUnit.SECONDS)).isNotInstanceOf(ForkJoinWorkerThread.class);
            assertThat(paused.completion).isNotDone();
        }
    }

    @Test
    void invoke_keepsAtomicCommitOnTheOwningPregelWorker() throws Exception {
        ProbeExecutable executable = new ProbeExecutable(0);
        Vertex vertex = new Vertex("owned", executable);
        Vertex.VertexSession session = createSession("owned");
        WorkflowCommitState state = mock(WorkflowCommitState.class,
                withSettings().extraInterfaces(Vertex.VertexState.class));
        when(session.state()).thenReturn((Vertex.VertexState) state);
        assertThat(vertex.init(session)).isTrue();

        vertex.invoke(new GraphState(), Map.of()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(executable.started.get(5, TimeUnit.SECONDS)).isSameAs(Thread.currentThread());
        verify(state).commitCmp();
    }

    @Test
    void call_withExplicitExecutor_keepsCallerExecutionPolicy() throws Exception {
        ProbeExecutable executable = new ProbeExecutable(0);
        Vertex vertex = new Vertex("inline", executable, Runnable::run);
        Vertex.VertexSession session = createSession("inline");
        assertThat(vertex.init(session)).isTrue();

        vertex.call(Map.of()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(executable.started.get(5, TimeUnit.SECONDS)).isSameAs(Thread.currentThread());
        verify(session.state()).setOutputs(Map.of("result", "ok"));
    }

    private Vertex createVertex(String nodeId, ProbeExecutable executable, boolean shouldUseNullExecutor) {
        Vertex vertex;
        if (shouldUseNullExecutor) {
            vertex = new Vertex(nodeId, executable, null);
        } else {
            vertex = new Vertex(nodeId, executable);
        }
        assertThat(vertex.init(createSession(nodeId))).isTrue();
        return vertex;
    }

    private Vertex.VertexSession createSession(String nodeId) {
        Vertex.VertexSession session = mock(Vertex.VertexSession.class);
        when(session.nodeSession(nodeId)).thenReturn(session);
        when(session.state()).thenReturn(mock(Vertex.VertexState.class));
        return session;
    }

    private static final class ProbeExecutable extends Executable<Map<String, Object>, Object> {
        private final CompletableFuture<Thread> started = new CompletableFuture<>();
        private final CountDownLatch release;

        private ProbeExecutable(int pauseCount) {
            release = new CountDownLatch(pauseCount);
        }

        /** {@inheritDoc} */
        @Override
        public Object onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            started.complete(Thread.currentThread());
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release the blocked node");
                }
            } catch (InterruptedException exception) {
                InterruptSignals.restoreCurrentThread();
                throw new IllegalStateException("Node execution was unexpectedly interrupted", exception);
            }
            return Map.of("result", "ok");
        }
    }

    /** Releases and drains the paused invocation, preserving any assertion failure as the primary error. */
    private static final class PausedCall implements AutoCloseable {
        private final ProbeExecutable executable;
        private final CompletableFuture<Void> completion;

        private PausedCall(ProbeExecutable executable, CompletableFuture<Void> completion) {
            this.executable = executable;
            this.completion = completion;
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws InterruptedException, ExecutionException, TimeoutException {
            executable.release.countDown();
            completion.get(5, TimeUnit.SECONDS);
        }
    }
}
