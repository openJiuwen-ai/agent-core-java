/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.TraceWorkflowSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ensures a vertex's final trace is published after its stream input trace.
 *
 * @since 2026-11-09
 */
class VertexStreamTraceCompletionTest {
    @Test
    void batchCompletionWaitsForInputTraceAndKeepsFinalStreamOutputs() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.pauseFinalInputTrace();
            Future<?> stream = fixture.startStream();
            assertTrue(fixture.inputTraceEntered.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.vertex.call(null).toCompletableFuture();

            // A slow trace sink must not let the completion snapshot overtake its input snapshot.
            assertThrows(TimeoutException.class, () -> batch.get(200, TimeUnit.MILLISECONDS));
            fixture.releaseInputTrace.countDown();
            stream.get(5, TimeUnit.SECONDS);
            batch.get(5, TimeUnit.SECONDS);
            List<TraceWorkflowSpan> spans = fixture.drainSpans();
            assertThat(spans).isNotEmpty();
            TraceWorkflowSpan last = spans.get(spans.size() - 1);
            assertThat(last.getEndTime()).isNotNull();
            assertThat(last.getStreamOutputs()).hasSize(1);
            assertThat(spans.stream().filter(span -> span.getEndTime() != null)).hasSize(1);
        }
    }

    @Test
    void failingInputTraceStillReleasesBatchCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            doAnswer(invocation -> {
                Map<?, ?> data = invocation.getArgument(2);
                if ("on_pre_stream".equals(invocation.getArgument(1)) && Boolean.TRUE.equals(data.get("need_send"))) {
                    throw new IllegalStateException("injected trace sink failure");
                }
                return invocation.callRealMethod();
            }).when(fixture.tracer).trigger(anyString(), anyString(), anyMap());
            Future<?> stream = fixture.startStream();
            assertTrue(fixture.outputProduced.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.vertex.call(null).toCompletableFuture();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> stream.get(5, TimeUnit.SECONDS));
            batch.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final CountDownLatch inputTraceEntered = new CountDownLatch(1);
        private final CountDownLatch releaseInputTrace = new CountDownLatch(1);
        private final CountDownLatch outputProduced = new CountDownLatch(1);
        private final StreamEmitter emitter = new StreamEmitter();
        private final Tracer tracer = spy(new Tracer("stream-trace-completion"));
        private final ThreadPoolExecutor executor;
        private final Vertex vertex;

        private Fixture() {
            AtomicInteger threadId = new AtomicInteger();
            executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(2), runnable ->
                            new Thread(runnable, "stream-trace-completion-" + threadId.incrementAndGet()));
            tracer.init(new StreamWriterManager(emitter, List.of(StreamMode.TRACE)));
            Vertex.VertexSession session = mock(Vertex.VertexSession.class);
            when(session.nodeSession("end")).thenReturn(session);
            when(session.executableId()).thenReturn("end");
            when(session.workflowId()).thenReturn("trace-workflow");
            when(session.parentId()).thenReturn("");
            when(session.state()).thenReturn(mock(Vertex.VertexState.class));
            when(session.actorManager()).thenReturn(mock(Vertex.VertexActorManager.class));
            when(session.tracer()).thenReturn(tracer);
            when(session.nodeConfig()).thenReturn(new Vertex.VertexNodeConfig(
                    List.of(ComponentAbility.TRANSFORM), new Vertex.VertexIoConfig(),
                    new Vertex.VertexIoConfig(), 0, -1.0d, null));
            vertex = new Vertex("end", new OneChunkExecutable(outputProduced), executor);
            vertex.setEndNode(true);
            vertex.init(session);
        }

        private void pauseFinalInputTrace() {
            doAnswer(invocation -> {
                Map<?, ?> data = invocation.getArgument(2);
                if ("on_pre_stream".equals(invocation.getArgument(1)) && Boolean.TRUE.equals(data.get("need_send"))) {
                    inputTraceEntered.countDown();
                    assertTrue(releaseInputTrace.await(5, TimeUnit.SECONDS));
                }
                return invocation.callRealMethod();
            }).when(tracer).trigger(anyString(), anyString(), anyMap());
        }

        private Future<?> startStream() {
            return executor.submit(() -> vertex.streamCall(new CountDownLatch(0), error -> {
                throw new AssertionError("Unexpected stream failure", error);
            }));
        }

        private List<TraceWorkflowSpan> drainSpans() {
            emitter.close();
            List<TraceWorkflowSpan> spans = new ArrayList<>();
            Object frame = emitter.getStreamQueue().receive(1_000L);
            while (!StreamEmitter.END_FRAME.equals(frame)) {
                assertThat(frame).isInstanceOf(TraceSchema.class);
                spans.add((TraceWorkflowSpan) ((TraceSchema) frame).getPayload());
                frame = emitter.getStreamQueue().receive(1_000L);
            }
            return spans;
        }

        @Override
        public void close() throws InterruptedException {
            releaseInputTrace.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class OneChunkExecutable extends Executable<Map<String, Object>, Object> {
        private final CountDownLatch outputProduced;

        private OneChunkExecutable(CountDownLatch outputProduced) {
            this.outputProduced = outputProduced;
        }

        @Override
        public Iterator<Object> onTransform(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            outputProduced.countDown();
            return List.<Object>of(Map.of("result", 6)).iterator();
        }
    }
}
