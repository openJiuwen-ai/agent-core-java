/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the bounded-wait invariants of the stream queue: a producer must
 * never spin forever on a full queue (critical frames), a consumer must never
 * block forever on a producer that will never send another frame, and a forced
 * critical frame must evict no more backlog than needed to be admitted.
 */
class AsyncStreamQueueTimeoutTest {

    @Test
    @DisplayName("sendCritical returns within its budget on a full queue, evicting only the head frame")
    void testSendCriticalForcedAfterTimeout() {
        AsyncStreamQueue queue = new AsyncStreamQueue(2);
        queue.send("frame-1");
        queue.send("frame-2");

        long startNanos = System.nanoTime();
        queue.sendCritical(StreamEmitter.END_FRAME, 300);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertTrue(elapsedMs >= 300, "sendCritical should honor the total budget, took " + elapsedMs + "ms");
        assertTrue(elapsedMs < 5000,
                "sendCritical should return within the budget plus one attempt, took " + elapsedMs + "ms");
        assertEquals("frame-2", queue.receive(100),
                "only the head frame should be evicted to admit the critical frame");
        assertEquals(StreamEmitter.END_FRAME, queue.receive(100));
        assertNull(queue.receive(100));
    }

    @Test
    @DisplayName("sendCritical keeps a paused consumer's backlog, evicting only the head frame")
    void testSendCriticalForcedAfterTimeoutKeepsBacklog() {
        AsyncStreamQueue queue = new AsyncStreamQueue(4);
        queue.send("frame-1");
        queue.send("frame-2");
        queue.send("frame-3");
        queue.send("frame-4");

        queue.sendCritical(StreamEmitter.END_FRAME, 300);

        // The queue was full, so exactly one head frame (frame-1) was evicted to
        // admit the critical frame; the remaining backlog survives in order.
        assertEquals("frame-2", queue.receive(100));
        assertEquals("frame-3", queue.receive(100));
        assertEquals("frame-4", queue.receive(100));
        assertEquals(StreamEmitter.END_FRAME, queue.receive(100));
        assertNull(queue.receive(100));
    }

    @Test
    @DisplayName("sendCritical succeeds without forcing when the consumer keeps draining")
    void testSendCriticalWithDrainingConsumer() throws Exception {
        AsyncStreamQueue queue = new AsyncStreamQueue(2);
        queue.send("frame-1");
        queue.send("frame-2");

        CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> queue.receive(2000));

        queue.sendCritical(StreamEmitter.END_FRAME, 5000);
        consumer.get(5, TimeUnit.SECONDS);

        assertEquals("frame-2", queue.receive(100));
        assertEquals(StreamEmitter.END_FRAME, queue.receive(100));
    }

    @Test
    @DisplayName("sendCritical on a closed queue throws IllegalStateException")
    void testSendCriticalOnClosedQueue() {
        AsyncStreamQueue queue = new AsyncStreamQueue();
        queue.close();
        assertThrows(IllegalStateException.class, () -> queue.sendCritical("critical"));
    }

    @Test
    @DisplayName("default timeouts must be positive so neither side can block forever")
    void testDefaultTimeoutsPositive() {
        assertTrue(AsyncStreamQueue.DEFAULT_RECEIVE_TIMEOUT_MS > 0);
        assertTrue(AsyncStreamQueue.DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS > 0);
    }

    @Test
    @DisplayName("resolveEffectiveReceiveTimeoutMs maps non-positive timeouts to the default")
    void testResolveEffectiveReceiveTimeoutMs() {
        assertEquals(AsyncStreamQueue.DEFAULT_RECEIVE_TIMEOUT_MS,
                AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(-1));
        assertEquals(AsyncStreamQueue.DEFAULT_RECEIVE_TIMEOUT_MS,
                AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(0));
        assertEquals(1L, AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(1));
        assertEquals(123_456L, AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(123_456L));
        assertEquals(Long.MAX_VALUE, AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(Long.MAX_VALUE));
        assertEquals(AsyncStreamQueue.DEFAULT_RECEIVE_TIMEOUT_MS,
                AsyncStreamQueue.resolveEffectiveReceiveTimeoutMs(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("receive with non-positive timeout wakes up when data arrives mid-wait")
    void testReceiveNonPositiveWakesOnData() throws Exception {
        AsyncStreamQueue queue = new AsyncStreamQueue();
        CompletableFuture<Void> sender = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            queue.send("late-frame");
        });

        long startNanos = System.nanoTime();
        Object result = queue.receive(-1);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals("late-frame", result);
        assertTrue(elapsedMs < 5000, "receive with non-positive timeout should wake on arriving data, took "
                + elapsedMs + "ms");
        sender.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("receive with positive timeout returns null when no data arrives")
    void testReceivePollTimeout() {
        AsyncStreamQueue queue = new AsyncStreamQueue();
        assertNull(queue.receive(50));
    }

    @Test
    @DisplayName("streamOutput throws first-frame timeout when no frame ever arrives")
    void testStreamOutputFirstFrameTimeout() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        long startNanos = System.nanoTime();
        assertThrows(BaseError.class, () -> manager.streamOutput(100, 100, true, item -> {
            // no frame is expected before the timeout fires
        }));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(elapsedMs < 5000, "streamOutput should fail on first-frame timeout, took " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("streamIterator throws frame-interval timeout when the producer stops without END_FRAME")
    void testStreamIteratorTimeoutOnDeadProducer() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("frame-1");

        Iterator<Object> iterator = manager.streamIterator(100, 100, true);
        assertTrue(iterator.hasNext());
        assertEquals("frame-1", iterator.next());

        long startNanos = System.nanoTime();
        assertThrows(BaseError.class, () -> iterator.hasNext());
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(elapsedMs < 5000, "iterator should fail on frame timeout, took " + elapsedMs + "ms");
    }
}
