/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests completion frames when the batch and stream sides of one vertex overlap. */
class VertexStreamCompletionTest {

    @Test
    void batchCallDoesNotDiscardOrFlushPendingStreamCompletion() throws Exception {
        PausedStreamExecutable executable = new PausedStreamExecutable();
        Vertex.VertexActorManager actorManager = mock(Vertex.VertexActorManager.class);
        Vertex.VertexSession session = mock(Vertex.VertexSession.class);
        when(session.nodeSession("mixed")).thenReturn(session);
        when(session.state()).thenReturn(mock(Vertex.VertexState.class));
        when(session.actorManager()).thenReturn(actorManager);
        when(session.nodeConfig()).thenReturn(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE, ComponentAbility.TRANSFORM, ComponentAbility.COLLECT),
                new Vertex.VertexIoConfig(), new Vertex.VertexIoConfig(), 0, -1.0d, null));
        AtomicInteger threadId = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable ->
                        new Thread(runnable, "vertex-completion-test-" + threadId.incrementAndGet()));
        Vertex vertex = new Vertex("mixed", executable, executor);
        vertex.init(session);

        try {
            Future<?> stream = executor.submit(() -> vertex.streamCall(new CountDownLatch(0), error -> {
                throw new AssertionError("Stream execution failed", error);
            }));
            assertTrue(executable.transformFinished.await(5, TimeUnit.SECONDS));
            Future<Void> batch = vertex.call(null).toCompletableFuture();
            assertTrue(executable.batchInvoked.await(5, TimeUnit.SECONDS));

            // The collect side has not finished; the batch call must leave its sibling's EOF alone.
            verify(actorManager, never()).endMessage("mixed", ComponentAbility.TRANSFORM);
            executable.finishStream.countDown();
            stream.get(5, TimeUnit.SECONDS);
            batch.get(5, TimeUnit.SECONDS);
            verify(actorManager).endMessage("mixed", ComponentAbility.TRANSFORM);
            verify(actorManager).markProducerDone("mixed");
        } finally {
            executable.finishStream.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class PausedStreamExecutable extends Executable<Map<String, Object>, Object>
            implements Vertex.StreamOutputProvider {
        private final CountDownLatch transformFinished = new CountDownLatch(1);
        private final CountDownLatch batchInvoked = new CountDownLatch(1);
        private final CountDownLatch finishStream = new CountDownLatch(1);

        @Override
        public Object onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            batchInvoked.countDown();
            return Map.of("batch", true);
        }

        @Override
        public Object onCollect(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            awaitFinish();
            return Map.of("collected", true);
        }

        @Override
        public Iterator<Object> onTransform(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            return Collections.emptyIterator();
        }

        @Override
        public Map<String, Object> getStreamOutput() {
            transformFinished.countDown();
            awaitFinish();
            return Map.of("streamed", true);
        }

        @Override
        public boolean skipTrace() {
            return true;
        }

        private void awaitFinish() {
            try {
                assertTrue(finishStream.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for stream completion", exception);
            }
        }
    }
}
