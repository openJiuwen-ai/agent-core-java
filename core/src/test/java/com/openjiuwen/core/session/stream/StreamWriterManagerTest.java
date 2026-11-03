/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.WorkflowSession;
import com.openjiuwen.harness.deep_agent.DeepAgentSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/session/stream/manager.py}.
 *
 * <p>Mirrors Python's {@code TestStreamOutput} in
 * {@code tests/unit_tests/core/session/stream/test_stream_output.py}.</p>
 */
class StreamWriterManagerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamWriterManagerTest.class);

    private final ThreadPoolExecutor closeExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1),
            StreamWriterManagerTest::newCloseThread);

    @AfterEach
    void stopCloseExecutor() {
        closeExecutor.shutdownNow();
        try {
            if (!closeExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Close worker did not stop");
            }
        } catch (InterruptedException exception) {
            throw new IllegalStateException("Close executor shutdown was interrupted", exception);
        }
    }

    @Test
    void sessionFacadesExposeBufferedOutputBeforeFailure() {
        AgentSession parent = new AgentSession("failed-workflow-parent", null, null);
        List<AgentSessionApi> sessions = List.of(new AgentSession("failed-agent", null, null),
                new AgentGroupSession("failed-group"), new WorkflowSession(parent.getInner(), "failed-workflow"),
                new AgentTeamSession("failed-team", null, "team"), new DeepAgentSession("failed-deep-agent"));
        for (AgentSessionApi session : sessions) {
            session.writeStream(new OutputSchema("test", 0, session.getSessionId()));
            IllegalStateException failure = new IllegalStateException(session.getSessionId());

            Iterator<Object> iterator = session.streamIterator(failure);

            OutputSchema output = assertInstanceOf(OutputSchema.class, iterator.next());
            if (session instanceof AgentTeamSession) {
                assertEquals(Map.of("value", "failed-team", "source_team_id", "team"), output.getPayload());
            } else {
                assertEquals(session.getSessionId(), output.getPayload());
            }
            assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    void terminalDrainRejectsFurtherWrites() {
        AsyncStreamQueue queue = new AsyncStreamQueue(1);
        queue.send("buffered");

        assertEquals(List.of("buffered"), queue.closeAndDrain());
        assertTrue(queue.isClosed());
        assertThrows(RuntimeException.class, () -> queue.send("late"));
        assertThrows(IllegalStateException.class, () -> queue.sendCritical(StreamEmitter.END_FRAME));
    }

    @Test
    @Timeout(2)
    void failedProducerDrainsFullQueueBeforeOriginalFailure() {
        StreamEmitter emitter = new StreamEmitter(new AsyncStreamQueue(2));
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        emitter.emit("second");
        IllegalStateException failure = new IllegalStateException("producer failed");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertTrue(emitter.isClosed());
        assertTrue(emitter.getStreamQueue().isClosed());
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertEquals("first", iterator.next());
        assertEquals("second", iterator.next());
        assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
        assertFalse(iterator.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
    }

    @Test
    void failedProducerWithNoOutputReportsFailureOnce() {
        StreamWriterManager manager = new StreamWriterManager(new StreamEmitter());
        IllegalStateException failure = new IllegalStateException("no output");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, iterator::next));
        assertFalse(iterator.hasNext());
    }

    @Test
    void queuedEndFrameDoesNotHideFailureOrLeakIntoOutput() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("chunk");
        emitter.close();
        IllegalStateException failure = new IllegalStateException("after close");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertEquals("chunk", iterator.next());
        assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
        assertFalse(iterator.hasNext());
    }

    @Test
    void streamOutputWithCustomWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getCustomWriter().write(Map.of("name", "Alice", "age", 30));
        manager.getCustomWriter().write(Map.of("name", "Bob", "age", 25));
        manager.getCustomWriter().write(Map.of("name", "Charlie", "age", 35));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        assertInstanceOf(CustomSchema.class, received.get(0));
        assertEquals("Alice", ((CustomSchema) received.get(0)).get("name"));
        assertEquals(25, ((CustomSchema) received.get(1)).get("age"));
        assertEquals("Charlie", ((CustomSchema) received.get(2)).get("name"));
    }

    @Test
    void streamOutputWithOutputWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getOutputWriter().write(Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"));
        manager.getOutputWriter().write(Map.of("type", "nodeB", "index", 1, "payload", "nodeB_stream"));
        manager.getOutputWriter().write(Map.of("type", "nodeC", "index", 1, "payload", "nodeC_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        OutputSchema first = assertInstanceOf(OutputSchema.class, received.get(0));
        assertEquals("nodeA", first.getType());
        assertEquals(1, first.getIndex());
        assertEquals("nodeA_stream", first.getPayload());
        OutputSchema third = assertInstanceOf(OutputSchema.class, received.get(2));
        assertEquals("nodeC", third.getType());
        assertEquals("nodeC_stream", third.getPayload());
    }

    @Test
    void streamOutputWithTraceWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getTraceWriter().write(Map.of("type", "on_chain_start", "payload", "nodeA_start"));
        manager.getTraceWriter().write(Map.of("type", "on_chain_end", "payload", "nodeA_end"));
        manager.getTraceWriter().write(Map.of("type", "on_chain_error", "payload", "nodeA_error"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        TraceSchema first = assertInstanceOf(TraceSchema.class, received.get(0));
        assertEquals("on_chain_start", first.getType());
        assertEquals("nodeA_start", first.getPayload());
        TraceSchema third = assertInstanceOf(TraceSchema.class, received.get(2));
        assertEquals("on_chain_error", third.getType());
        assertEquals("nodeA_error", third.getPayload());
    }

    @Test
    void streamOutputWithMockWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        String mockMode = "mock";
        manager.addWriter(mockMode, new StreamWriter<>(emitter, MockSchema.class, MockSchema::fromMap));

        manager.getWriter(mockMode).write(Map.of("data", "nodeA_stream"));
        manager.getWriter(mockMode).write(Map.of("data", "nodeB_stream"));
        manager.getWriter(mockMode).write(Map.of("data", "nodeC_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertTrue(manager.getEnabledModes().contains(mockMode));
        assertEquals(3, received.size());
        MockSchema first = assertInstanceOf(MockSchema.class, received.get(0));
        assertEquals("nodeA_stream", first.data());
        MockSchema third = assertInstanceOf(MockSchema.class, received.get(2));
        assertEquals("nodeC_stream", third.data());
    }

    @Test
    @Timeout(2)
    void shortFirstFrameTimeoutKeepsOriginalError() {
        StreamWriterManager manager = new StreamWriterManager(new StreamEmitter());

        BaseError error = assertThrows(BaseError.class,
                () -> manager.streamIterator(30L, 1000L, false).hasNext());

        assertEquals(StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
        assertEquals("0.03", error.getParams().get("timeout"));
    }

    @Test
    @Timeout(2)
    void shortSubsequentTimeoutKeepsOriginalError() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 30L, false);
        assertEquals("first", iterator.next());

        BaseError error = assertThrows(BaseError.class, iterator::hasNext);

        assertEquals(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
        assertEquals("0.03", error.getParams().get("timeout"));
    }

    @Test
    @Timeout(8)
    void shorterLeaseDoesNotShortenOriginalSubsequentDeadline() {
        FrameOnCallQueue queue = new FrameOnCallQueue(8, "original-deadline");
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 6200L, false);
        assertEquals("first", iterator.next());
        long leaseId = manager.renewModelCallTimeout(0L, 1L, 0L);

        assertEquals("original-deadline", iterator.next());
        assertTrue(leaseId > 0L);
        assertEquals(leaseId, manager.renewModelCallTimeout(leaseId, 1L, 0L));
        assertEquals(8, queue.receiveCalls);
    }

    @Test
    @Timeout(2)
    void activeLeaseDoesNotExtendFirstFrameTimeout() {
        StreamWriterManager manager = new StreamWriterManager(new StreamEmitter());
        manager.renewModelCallTimeout(0L, 1000L, 0L);

        BaseError error = assertThrows(BaseError.class,
                () -> manager.streamIterator(30L, 1000L, false).hasNext());

        assertEquals(StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
    }

    @Test
    @Timeout(7)
    void expiredLeaseRestoresOriginalTimeoutWithoutBusyLoop() {
        FrameOnCallQueue queue = new FrameOnCallQueue(-1, "unused");
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 40L, false);
        assertEquals("first", iterator.next());
        manager.renewModelCallTimeout(0L, 1L, 0L);

        BaseError error = assertThrows(BaseError.class, iterator::hasNext);

        assertEquals(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
        assertEquals("0.04", error.getParams().get("timeout"));
        assertTrue(queue.receiveCalls <= 8);
    }

    @Test
    @Timeout(2)
    void queuedFrameIsDrainedWhenDeadlineExpires() {
        FrameAfterWaitQueue queue = new FrameAfterWaitQueue();
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 20L, false);
        assertEquals("first", iterator.next());
        queue.enqueueAfterNull = true;

        assertEquals("queued-before-timeout", iterator.next());
    }

    @Test
    @Timeout(3)
    void tryReceiveAccountsForConsumedFrameDuringClose() throws Exception {
        AsyncStreamQueue queue = new AsyncStreamQueue();
        assertNull(queue.tryReceive());
        queue.send("consumed");
        queue.send("remaining");
        assertEquals("consumed", queue.tryReceive());
        Future<?> close = closeExecutor.submit(() -> queue.close(2000L));
        awaitCloseStarted(queue);
        assertFalse(close.isDone(), "Close finished before the remaining frame was drained");

        assertEquals(List.of("remaining"), queue.closeAndDrain());

        assertDoesNotThrow(() -> close.get(500L, TimeUnit.MILLISECONDS));
    }

    @Test
    @Timeout(3)
    void invalidLeaseOperationsPreserveExistingLease() {
        FrameOnCallQueue queue = new FrameOnCallQueue(3, "preserved-lease");
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 60L, false);
        assertEquals("first", iterator.next());
        long leaseId = manager.renewModelCallTimeout(0L, 100L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> manager.renewModelCallTimeout(leaseId + 1L, 100L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.renewModelCallTimeout(leaseId, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.renewModelCallTimeout(leaseId, -1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.renewModelCallTimeout(leaseId, 100L, -1L));
        manager.endModelCallTimeout(0L);
        manager.endModelCallTimeout(leaseId + 1L);

        assertEquals("preserved-lease", iterator.next());
        manager.endModelCallTimeout(leaseId);
    }

    @Test
    @Timeout(8)
    void multipleLeasesUseLatestDeadline() {
        LatestLeaseQueue queue = new LatestLeaseQueue();
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 5100L, false);
        assertEquals("first", iterator.next());
        manager.renewModelCallTimeout(0L, 1L, 0L);
        manager.renewModelCallTimeout(0L, 1000L, 0L);

        assertEquals("latest-lease", iterator.next());
        assertEquals(7, queue.receiveCalls);
    }

    @Test
    void customSessionDefaultsToNoLeaseCoordination() {
        MinimalSession session = new MinimalSession();

        assertEquals(0L, session.renewModelCallTimeout(0L, 1L, 0L));
        session.endModelCallTimeout(0L);
        session.endModelCallTimeout(1L);
    }

    private static void awaitCloseStarted(AsyncStreamQueue queue) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (!queue.isClosed() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(1_000_000L);
        }
        assertTrue(queue.isClosed(), "Close did not start within one second");
    }

    private static Thread newCloseThread(Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("stream-close-test-worker");
        thread.setUncaughtExceptionHandler((worker, error) -> LOGGER.error("Stream close worker failed", error));
        return thread;
    }

    private static List<Object> drain(StreamWriterManager manager) {
        Iterator<Object> iterator = manager.streamIterator();
        java.util.ArrayList<Object> items = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            items.add(iterator.next());
        }
        return items;
    }

    private static final class FrameOnCallQueue extends AsyncStreamQueue {
        private final int frameOnCall;
        private final String frame;
        private int receiveCalls;

        private FrameOnCallQueue(int frameOnCall, String frame) {
            this.frameOnCall = frameOnCall;
            this.frame = frame;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            if (receiveCalls == frameOnCall) {
                send(frame);
            }
            return super.receive(timeoutMs);
        }
    }

    private static final class LatestLeaseQueue extends AsyncStreamQueue {
        private int receiveCalls;

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            if (receiveCalls == 7) {
                send("latest-lease");
            }
            Object item = super.receive(timeoutMs);
            if (receiveCalls == 6 && item == null) {
                awaitPastOriginalDeadline();
            }
            return item;
        }

        private static void awaitPastOriginalDeadline() {
            try {
                if (new CountDownLatch(1).await(250L, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Unexpected deadline latch release");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Deadline coordination was interrupted", exception);
            }
        }
    }

    private static final class FrameAfterWaitQueue extends AsyncStreamQueue {
        private boolean enqueueAfterNull;

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            Object item = super.receive(timeoutMs);
            if (enqueueAfterNull && item == null) {
                enqueueAfterNull = false;
                send("queued-before-timeout");
            }
            return item;
        }
    }

    private static final class MinimalSession implements AgentSessionApi {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getSessionId() {
            return "minimal";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getState(String key) {
            return Map.of();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void updateState(Map<String, Object> data) {
            // No state is needed for the default lease contract.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeStream(Object data) {
            // No stream output is needed for the default lease contract.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Iterator<Object> streamIterator() {
            return java.util.Collections.emptyIterator();
        }
    }

    private record MockSchema(String data) {
        private static MockSchema fromMap(Map<String, Object> value) {
            return new MockSchema(String.valueOf(value.get("data")));
        }
    }
}
