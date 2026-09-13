/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.exception.GraphError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link StreamProcessor}.
 */
class StreamProcessorTest {
    @Test
    @DisplayName("generator routes stream chunks to schema iterators and ends cleanly")
    void testGeneratorRoutesChunks() throws Exception {
        StreamProcessor processor =
                new StreamProcessor("collector", List.of(Set.of("producer-STREAM")), 1);
        List<Object> callbacks = new CopyOnWriteArrayList<>();

        Map<String, Object> generated = processor.generator(
                Map.of("value", "${producer.value}", "nested", Map.of("other", "${producer.other}"), "static", 1),
                callbacks::add);

        @SuppressWarnings("unchecked")
        Iterator<Object> valueIterator = (Iterator<Object>) generated.get("value");
        @SuppressWarnings("unchecked")
        Iterator<Object> otherIterator =
            (Iterator<Object>) ((Map<String, Object>) generated.get("nested")).get("other");

        CompletableFuture<Void> runner = CompletableFuture.runAsync(() -> processor.run(ComponentAbility.STREAM));

        processor.receive(
                new StreamPayload(Map.of("producer", Map.of("value", "alpha", "other", 2)), ComponentAbility.STREAM));

        assertTrue(valueIterator.hasNext());
        assertEquals("alpha", valueIterator.next());
        assertTrue(otherIterator.hasNext());
        assertEquals(2, otherIterator.next());
        assertEquals(1, generated.get("static"));
        assertEquals(List.of(Map.of("value", "alpha"), Map.of("nested.other", 2)), callbacks);

        processor.receive(new StreamPayload(Map.of("producer", "END_STREAM"), ComponentAbility.STREAM));

        assertFalse(valueIterator.hasNext());
        assertFalse(otherIterator.hasNext());
        runner.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("restored source completion closes its iterator while resumed sibling continues")
    void restoredSourceCompletionClosesIterator() throws Exception {
        StreamProcessor processor = new StreamProcessor("collector",
                List.of(Set.of("producer1-STREAM"), Set.of("producer2-STREAM")), 1);
        processor.seedCompletedSources(Set.of("producer1-STREAM"));

        Map<String, Object> generated = processor.generator(
                Map.of("value1", "${producer1.value}", "value2", "${producer2.value}"), null);
        @SuppressWarnings("unchecked")
        Iterator<Object> value1 = (Iterator<Object>) generated.get("value1");
        @SuppressWarnings("unchecked")
        Iterator<Object> value2 = (Iterator<Object>) generated.get("value2");

        CompletableFuture<Void> runner = CompletableFuture.runAsync(() -> processor.run(ComponentAbility.TRANSFORM));
        processor.receive(
                new StreamPayload(Map.of("producer2", Map.of("value", "resumed")), ComponentAbility.STREAM));

        assertFalse(value1.hasNext());
        assertTrue(value2.hasNext());
        assertEquals("resumed", value2.next());

        processor.receive(new StreamPayload(Map.of("producer2", "END_STREAM"), ComponentAbility.STREAM));
        assertFalse(value2.hasNext());
        runner.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("interrupting a processor closes iterators waiting for unfinished sources")
    void interruptClosesPendingSourceIterator() throws Exception {
        StreamProcessor processor = new StreamProcessor("collector",
                List.of(Set.of("producer1-STREAM"), Set.of("producer2-STREAM")), 0);
        Map<String, Object> generated = processor.generator(
                Map.of("value1", "${producer1.value}", "value2", "${producer2.value}"), null);
        @SuppressWarnings("unchecked")
        Iterator<Object> value1 = (Iterator<Object>) generated.get("value1");
        @SuppressWarnings("unchecked")
        Iterator<Object> value2 = (Iterator<Object>) generated.get("value2");

        Thread processorThread = new Thread(() -> processor.run(ComponentAbility.TRANSFORM),
                "stream-processor-interrupt-test");
        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicBoolean pendingIteratorHasNext = new AtomicBoolean(true);
        Thread consumerThread = new Thread(() -> {
            consumerStarted.countDown();
            pendingIteratorHasNext.set(value2.hasNext());
        }, "stream-processor-consumer-test");

        try {
            processorThread.start();
            processor.receive(
                    new StreamPayload(Map.of("producer1", Map.of("value", "completed")), ComponentAbility.STREAM));
            processor.receive(new StreamPayload(Map.of("producer1", "END_STREAM"), ComponentAbility.STREAM));
            assertTrue(value1.hasNext());
            assertEquals("completed", value1.next());

            consumerThread.start();
            assertTrue(consumerStarted.await(1, TimeUnit.SECONDS));
            processorThread.interrupt();
            processorThread.join(TimeUnit.SECONDS.toMillis(1));
            consumerThread.join(TimeUnit.SECONDS.toMillis(1));

            assertFalse(processorThread.isAlive());
            assertFalse(consumerThread.isAlive(), "pending iterator should be closed when processor is interrupted");
            assertFalse(pendingIteratorHasNext.get());
        } finally {
            processorThread.interrupt();
            consumerThread.interrupt();
            processorThread.join(TimeUnit.SECONDS.toMillis(1));
            consumerThread.join(TimeUnit.SECONDS.toMillis(1));
        }
    }

    @Test
    @DisplayName("helper methods identify end messages and source ownership")
    void testHelperMethods() {
        Map<String, Object> endMessage = Map.of("producer", "END_STREAM");

        assertTrue(StreamProcessor.isEndMessage(endMessage));
        assertEquals("producer", StreamProcessor.getProducerId(endMessage));
        assertTrue(StreamProcessor.isValueFromSource("producer.output", "producer"));
        assertFalse(StreamProcessor.isValueFromSource("another.output", "producer"));
    }

    // ---- P2-b: TIMEOUT_SENTINEL behavior tests ----

    /**
     * Access the internal processor queue for a given schema reference path via reflection.
     * Used by timeout-sentinel tests to directly offer sentinels into the iterator's queue,
     * simulating what closeAllQueuesWithTimeout() does after a main-loop poll timeout.
     */
    @SuppressWarnings("unchecked")
    private BlockingQueue<Object> getProcessorQueue(StreamProcessor processor, String refPath) throws Exception {
        var field = StreamProcessor.class.getDeclaredField("processorQueues");
        field.setAccessible(true);
        Map<String, List<BlockingQueue<Object>>> queues =
                (Map<String, List<BlockingQueue<Object>>>) field.get(processor);
        return queues.get(refPath).get(0);
    }

    @Test
    @DisplayName("TIMEOUT_SENTINEL causes hasNext to throw GraphError with STREAM_PROCESSOR_QUEUE_TIMEOUT")
    void timeoutSentinelRaisesGraphError() throws Exception {
        StreamProcessor processor = new StreamProcessor("test-timeout",
                List.of(Set.of("producer-STREAM")), 0);
        Map<String, Object> generated = processor.generator(
                Map.of("value", "${producer.value}"), null);
        @SuppressWarnings("unchecked")
        Iterator<Object> valueIterator = (Iterator<Object>) generated.get("value");

        // Offer TIMEOUT_SENTINEL directly to the iterator's queue, simulating
        // what closeAllQueuesWithTimeout() does after a main-loop poll timeout.
        BlockingQueue<Object> iterQueue = getProcessorQueue(processor, "${producer.value}");
        iterQueue.offer(StreamProcessor.TIMEOUT_SENTINEL);

        GraphError thrown = assertThrows(GraphError.class, valueIterator::hasNext);
        assertEquals(StatusCode.STREAM_PROCESSOR_QUEUE_TIMEOUT, thrown.getStatus());
        assertEquals(StatusCode.STREAM_PROCESSOR_QUEUE_TIMEOUT.getCode(), thrown.getCode());
    }

    @Test
    @DisplayName("hasNext returns false after TIMEOUT_SENTINEL has been consumed (idempotent)")
    void hasNextReturnsFalseAfterTimeoutSentinelConsumed() throws Exception {
        StreamProcessor processor = new StreamProcessor("test-timeout-idempotent",
                List.of(Set.of("producer-STREAM")), 0);
        Map<String, Object> generated = processor.generator(
                Map.of("value", "${producer.value}"), null);
        @SuppressWarnings("unchecked")
        Iterator<Object> valueIterator = (Iterator<Object>) generated.get("value");

        BlockingQueue<Object> iterQueue = getProcessorQueue(processor, "${producer.value}");
        iterQueue.offer(StreamProcessor.TIMEOUT_SENTINEL);

        // First call throws GraphError (done=true is set before throw)
        assertThrows(GraphError.class, valueIterator::hasNext);
        // Second call returns false — iterator is done, no double-throw
        assertFalse(valueIterator.hasNext());
    }

    @Test
    @DisplayName("TIMEOUT_SENTINEL and END_SENTINEL are distinct objects")
    void timeoutAndEndSentinelsAreDistinct() {
        assertNotSame(StreamProcessor.TIMEOUT_SENTINEL, StreamProcessor.END_SENTINEL);
        assertNotEquals(StreamProcessor.TIMEOUT_SENTINEL, StreamProcessor.END_SENTINEL);
    }

    @Test
    @DisplayName("closeAllQueuesWithTimeout offers TIMEOUT_SENTINEL to all consumer queues")
    void closeAllQueuesWithTimeoutOffersTimeoutSentinelToAllQueues() throws Exception {
        StreamProcessor processor = new StreamProcessor("test-timeout-close-all",
                List.of(Set.of("producer-STREAM")), 0);
        // Create two iterator queues via generator
        processor.generator(
                Map.of("value", "${producer.value}", "other", "${producer.other}"), null);

        // Invoke closeAllQueuesWithTimeout() via reflection
        var method = StreamProcessor.class.getDeclaredMethod("closeAllQueuesWithTimeout");
        method.setAccessible(true);
        method.invoke(processor);

        // Verify all queues received TIMEOUT_SENTINEL (not END_SENTINEL)
        var field = StreamProcessor.class.getDeclaredField("processorQueues");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<BlockingQueue<Object>>> queues =
                (Map<String, List<BlockingQueue<Object>>>) field.get(processor);
        for (List<BlockingQueue<Object>> queueList : queues.values()) {
            for (BlockingQueue<Object> q : queueList) {
                assertSame(StreamProcessor.TIMEOUT_SENTINEL, q.poll(),
                        "queue should contain TIMEOUT_SENTINEL, not END_SENTINEL");
            }
        }
    }
}
