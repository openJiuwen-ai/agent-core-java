/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowCancellation;
import com.openjiuwen.core.session.WorkflowSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies ordering between a mixed End's live stream and batch trailer.
 *
 * @since 2026-10-30
 */
class VertexEndOutputOrderTest {
    @Test
    void batchWaitsForScheduledStreamBeforeReadingItsCompletion() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(6), 0)) {
            CountDownLatch checkingStartup = new CountDownLatch(1);
            CountDownLatch streamReady = new CountDownLatch(1);
            doAnswer(invocation -> {
                checkingStartup.countDown();
                assertTrue(streamReady.await(5, TimeUnit.SECONDS));
                return null;
            }).when(fixture.manager).awaitStreamReady("end");
            Future<Void> batch = fixture.startBatch();
            assertTrue(checkingStartup.await(5, TimeUnit.SECONDS));
            assertThat(fixture.vertex.streamCallCount()).isZero();
            assertThat(fixture.frames).isEmpty();
            fixture.startStream();
            streamReady.countDown();
            fixture.releaseSource.countDown();
            fixture.finish(batch);
            assertThat(fixture.nextPayload()).isEqualTo(Map.of("output", Map.of("result", 6)));
            assertThat(fixture.nextPayload()).isEqualTo(trailer());
            assertThat(fixture.frames).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void batchTrailerWaitsForStreamWithoutBufferingLiveFrames(int pauseAt) throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(6, 9), pauseAt)) {
            fixture.startStream();
            if (pauseAt == 1) {
                assertThat(fixture.nextPayload()).isEqualTo(Map.of("output", Map.of("result", 6)));
            }
            assertTrue(fixture.sourceWaiting.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.startBatch();

            // With the source paused, the batch trailer must not overtake a live stream field.
            assertThat(fixture.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
            fixture.releaseSource.countDown();
            if (pauseAt == 0) {
                assertThat(fixture.nextPayload()).isEqualTo(Map.of("output", Map.of("result", 6)));
            }
            assertThat(fixture.nextPayload()).isEqualTo(Map.of("output", Map.of("result", 9)));
            assertThat(fixture.nextPayload()).isEqualTo(trailer());
            fixture.finish(batch);
            assertThat(fixture.frames).isEmpty();
        }
    }

    @Test
    void emptyStreamStillReleasesBatchTrailer() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(), 0)) {
            fixture.startStream();
            assertTrue(fixture.sourceWaiting.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.startBatch();
            fixture.releaseSource.countDown();
            fixture.finish(batch);
            assertThat(fixture.nextPayload()).isEqualTo(trailer());
            assertThat(fixture.frames).isEmpty();
        }
    }

    @Test
    void inactiveStreamDoesNotBlockBatchOnlyEnd() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(), 0)) {
            fixture.startBatch().get(5, TimeUnit.SECONDS);
            assertThat(fixture.nextPayload()).isEqualTo(trailer());
        }
    }

    @Test
    void resetUsesTheNextStreamCompletionSignal() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(6), 0)) {
            fixture.releaseSource.countDown();
            for (int round = 0; round < 2; round++) {
                fixture.vertex.reset();
                fixture.startStream();
                fixture.finish(fixture.startBatch());
                assertThat(fixture.nextPayload()).isEqualTo(Map.of("output", Map.of("result", 6)));
                assertThat(fixture.nextPayload()).isEqualTo(trailer());
                assertThat(fixture.frames).isEmpty();
            }
        }
    }

    @Test
    void streamFailurePreventsSuccessfulBatchTrailer() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(6), 0)) {
            fixture.sourceFailure = new IllegalStateException("injected End source failure");
            fixture.startStream();
            assertTrue(fixture.sourceWaiting.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.startBatch();
            fixture.releaseSource.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> batch.get(5, TimeUnit.SECONDS));
            fixture.stream.get(5, TimeUnit.SECONDS);
            assertThat(failure).hasRootCause(fixture.sourceFailure);
            assertThat(fixture.streamError.get()).isNotNull();
            assertThat(fixture.frames).isEmpty();
        }
    }

    @Test
    void cancellationInterruptsBothSourceAndBatchWaiter() throws Exception {
        try (Fixture fixture = new Fixture(null, List.of(6), 0)) {
            fixture.startStream();
            assertTrue(fixture.sourceWaiting.await(5, TimeUnit.SECONDS));
            Future<Void> batch = fixture.startBatch();
            fixture.cancellation.cancel();
            fixture.cancellation.awaitQuiescence(Duration.ofSeconds(5));
            assertThrows(CancellationException.class, () -> batch.get(5, TimeUnit.SECONDS));
            fixture.stream.get(5, TimeUnit.SECONDS);
            assertThat(fixture.streamError.get()).isNotNull();
            assertThat(fixture.frames).isEmpty();
        }
    }

    @Test
    void templateEndKeepsBothSourcesConcurrent() throws Exception {
        EndConfig config = new EndConfig("value={{result}}; final={{final_result}}");
        try (Fixture fixture = new Fixture(config, List.of(6, 9), 0)) {
            fixture.startStream();
            Future<Void> batch = fixture.startBatch();
            fixture.releaseSource.countDown();
            fixture.finish(batch);
            StringBuilder rendered = new StringBuilder();
            for (Object frame : fixture.frames) {
                OutputSchema output = (OutputSchema) frame;
                Map<?, ?> payload = (Map<?, ?>) output.getPayload();
                rendered.append(payload.get("response"));
            }
            assertThat(rendered.toString()).isEqualTo("value=69; final=done");
        }
    }

    @Test
    void preparedTemplateLiteralIsEmittedBeforeTheFollowingBatchValue() throws Exception {
        EndConfig config = new EndConfig("value={{result}}; final={{final_result}}");
        try (Fixture fixture = new Fixture(config, List.of(6, 9), 0)) {
            fixture.pauseTemplateLiteral = true;
            fixture.releaseSource.countDown();
            fixture.startStream();
            Future<Void> batch = fixture.startBatch();
            assertTrue(fixture.templateLiteralPrepared.await(5, TimeUnit.SECONDS));
            StringBuilder rendered = new StringBuilder();
            for (int index = 0; index < 3; index++) {
                rendered.append(((Map<?, ?>) fixture.nextPayload()).get("response"));
            }
            assertThat(rendered.toString()).isEqualTo("value=69");
            assertThat(fixture.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
            fixture.releaseTemplateLiteral.countDown();
            fixture.finish(batch);
            while (!fixture.frames.isEmpty()) {
                rendered.append(((Map<?, ?>) fixture.nextPayload()).get("response"));
            }
            assertThat(rendered.toString()).isEqualTo("value=69; final=done");
        }
    }

    private static Map<String, Object> trailer() {
        return Map.of("output", Collections.singletonMap("final_result", null));
    }

    private static final class Fixture implements AutoCloseable {
        private final CountDownLatch sourceWaiting = new CountDownLatch(1);
        private final CountDownLatch releaseSource = new CountDownLatch(1);
        private final CountDownLatch batchEntered = new CountDownLatch(1);
        private final CountDownLatch templateLiteralPrepared = new CountDownLatch(1);
        private final CountDownLatch releaseTemplateLiteral = new CountDownLatch(1);
        private final BlockingQueue<Object> frames = new ArrayBlockingQueue<>(16);
        private final AtomicReference<Exception> streamError = new AtomicReference<>();
        private final WorkflowCancellation cancellation = new WorkflowCancellation();
        private final Vertex.VertexActorManager manager = mock(Vertex.VertexActorManager.class);
        private final ThreadPoolExecutor executor;
        private final Vertex vertex;
        private Future<?> stream;
        private IllegalStateException sourceFailure;
        private boolean pauseTemplateLiteral;

        private Fixture(EndConfig config, List<Integer> values, int pauseAt) throws Exception {
            AtomicInteger threadId = new AtomicInteger();
            executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(4), runnable ->
                            new Thread(runnable, "end-order-test-" + threadId.incrementAndGet()));
            End end = new End(config) {
                @Override
                public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs) {
                    Iterator<Object> output = super.onStream(inputs, session, kwargs);
                    batchEntered.countDown();
                    return templateIterator(output, true);
                }

                @Override
                public Iterator<Object> onTransform(Object inputs, BaseSession session, Object... kwargs) {
                    return templateIterator(super.onTransform(inputs, session, kwargs), false);
                }
            };
            Vertex.VertexSession session = mock(Vertex.VertexSession.class);
            Vertex.VertexState state = mock(Vertex.VertexState.class);
            StreamEmitter emitter = mock(StreamEmitter.class);
            when(session.nodeSession("end")).thenReturn(session);
            when(session.state()).thenReturn(state);
            when(session.workflowId()).thenReturn("end-order-workflow");
            when(session.actorManager()).thenReturn(manager);
            when(session.subGraph()).thenReturn(true);
            Config sessionConfig = new Config();
            sessionConfig.setEnvs(cancellation.attach(new WorkflowSession("end-order")).getEnvs());
            when(session.config()).thenReturn(sessionConfig);
            when(session.nodeConfig()).thenReturn(new Vertex.VertexNodeConfig(
                    List.of(ComponentAbility.STREAM, ComponentAbility.TRANSFORM),
                    new Vertex.VertexIoConfig(Map.of("final_result", "batch"), null),
                    new Vertex.VertexIoConfig(Map.of("result", "source"), null),
                    0, -1.0d, null));
            when(state.getInputs(any())).thenReturn(Collections.singletonMap("final_result",
                    config == null ? null : "done"));
            when(manager.consume(eq("end"), eq(ComponentAbility.TRANSFORM), any(), any()))
                    .thenAnswer(invocation -> Map.of("result", source(values, pauseAt)));
            when(manager.subWorkflowStream()).thenReturn(emitter);
            doAnswer(invocation -> {
                Object frame = invocation.getArgument(0);
                if (!StreamEmitter.END_FRAME.equals(frame)) {
                    frames.add(frame);
                }
                return null;
            }).when(emitter).emit(any());
            vertex = new Vertex("end", end, executor);
            vertex.setEndNode(true);
            vertex.init(session);
        }

        private Iterator<Integer> source(List<Integer> values, int pauseAt) {
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    if (index == pauseAt) {
                        sourceWaiting.countDown();
                        awaitSource();
                        if (sourceFailure != null) {
                            throw sourceFailure;
                        }
                    }
                    return index < values.size();
                }

                @Override
                public Integer next() {
                    return values.get(index++);
                }
            };
        }

        private void awaitSource() {
            awaitRelease(releaseSource);
        }

        private void awaitRelease(CountDownLatch release) {
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                InterruptSignals.restoreCurrentThread();
                throw new IllegalStateException("End source interrupted", exception);
            }
        }

        private Iterator<Object> templateIterator(Iterator<Object> output, boolean batch) {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    if (pauseTemplateLiteral && batch) {
                        awaitRelease(templateLiteralPrepared);
                    }
                    return output.hasNext();
                }

                @Override
                public Object next() {
                    Object frame = output.next();
                    if (pauseTemplateLiteral && !batch && frame instanceof OutputSchema schema
                            && schema.getPayload() instanceof Map<?, ?> payload
                            && "; final=".equals(payload.get("response"))) {
                        templateLiteralPrepared.countDown();
                        awaitRelease(releaseTemplateLiteral);
                    }
                    return frame;
                }
            };
        }

        private void startStream() throws InterruptedException {
            CountDownLatch ready = new CountDownLatch(1);
            stream = executor.submit(() -> vertex.streamCall(ready, streamError::set));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
        }

        private Future<Void> startBatch() throws InterruptedException {
            Future<Void> batch = vertex.call(null).toCompletableFuture();
            assertTrue(batchEntered.await(5, TimeUnit.SECONDS));
            return batch;
        }

        private Object nextPayload() throws InterruptedException {
            Object frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).isNotNull();
            return frame instanceof OutputSchema output ? output.getPayload() : frame;
        }

        private void finish(Future<Void> batch) throws Exception {
            batch.get(5, TimeUnit.SECONDS);
            stream.get(5, TimeUnit.SECONDS);
            assertThat(streamError.get()).isNull();
        }

        @Override
        public void close() throws InterruptedException {
            releaseSource.countDown();
            templateLiteralPrepared.countDown();
            releaseTemplateLiteral.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
