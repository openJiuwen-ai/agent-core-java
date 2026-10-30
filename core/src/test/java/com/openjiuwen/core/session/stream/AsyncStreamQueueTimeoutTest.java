/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the bounded-wait invariant of {@link AsyncStreamQueue#sendCritical(Object)}:
 * a producer must never spin forever on a full queue with a dead consumer. The total
 * wait is bounded by {@link AsyncStreamQueue#DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS};
 * when the budget expires the oldest data frames are evicted just enough to admit the
 * critical frame, keeping the remaining backlog in order.
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
}
