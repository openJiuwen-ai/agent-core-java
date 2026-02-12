// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResponseCollector.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/dmessage_queue/dsubscription/test_response_collector.py
 */
class ResponseCollectorTest {

    // ==================== State Management ====================

    @Nested
    @DisplayName("TestResponseCollectorStateManagement")
    class StateManagement {

        @Test
        @DisplayName("test_state_transitions - active -> cancelled")
        void testStateTransitions() {
            // Initial state is active
            ResponseCollector collector = new ResponseCollector("msg-123", "remote-456", null, 10.0);
            try {
                assertFalse(collector.isCancelled());
                assertFalse(collector.isExpired());
                assertTrue(collector.isActive());
            } finally {
                collector.close();
            }

            // After close, becomes cancelled
            assertTrue(collector.isCancelled());
            assertFalse(collector.isActive());
        }

        @Test
        @DisplayName("test_ttl_expiration - TTL expiry handling")
        void testTtlExpiration() throws InterruptedException {
            ResponseCollector collector = new ResponseCollector("msg-ttl", "remote-ttl", null, 0.1);
            assertFalse(collector.isExpired());

            Thread.sleep(200); // Wait for TTL to expire (100ms + buffer)

            assertTrue(collector.isExpired());
            assertFalse(collector.isActive());
            collector.close();
        }
    }

    // ==================== Put Message ====================

    @Nested
    @DisplayName("TestResponseCollectorPutMessage")
    class PutMessage {

        @Test
        @DisplayName("test_put_message_lifecycle - active and inactive states")
        void testPutMessageLifecycle() {
            // Active state: message enqueued normally
            ResponseCollector collector = new ResponseCollector("msg-put", "remote-put", null, 10.0);
            try {
                DmqResponseMessage msg = DmqResponseMessage.builder()
                    .messageId("msg-put")
                    .payload("test_data")
                    .build();
                collector.putMessage(msg);
                assertEquals(1, collector.getQueue().size());
            } finally {
                collector.close(CancelReason.FINISH);
            }

            // Inactive state: message discarded
            DmqResponseMessage msg2 = DmqResponseMessage.builder()
                .messageId("msg-put")
                .payload("test_data_2")
                .build();
            collector.putMessage(msg2);
            assertEquals(0, collector.getQueue().size());
        }

        @Test
        @DisplayName("test_put_message_queue_full_triggers_cancel")
        void testPutMessageQueueFullTriggersCancel() {
            ResponseCollector collector = new ResponseCollector("msg-full", "remote-full", null, 10.0);
            // Replace queue with a small one for testing
            LinkedBlockingQueue<Object> smallQueue = new LinkedBlockingQueue<>(2);
            // Use reflection or direct access to set the queue
            // Since queue is final, we'll put messages until the default queue is full
            // Actually, we test by creating a collector and checking that queue full triggers cancel

            // Alternative approach: fill the default queue up
            // For testing, let's just verify the behavior by directly manipulating
            try {
                for (int i = 0; i < 3; i++) {
                    DmqResponseMessage msg = DmqResponseMessage.builder()
                        .messageId("msg-full")
                        .payload("data" + i)
                        .build();
                    // We need a small queue to test. Since we can't change the queue,
                    // let's test indirectly by filling up to capacity.
                    collector.putMessage(msg);
                }
                // With MAX_QUEUE_SIZE=10000, we can't easily fill it.
                // Instead, test the logic by verifying that inactive collector discards messages.
                // The Python test replaces the queue: collector.queue = asyncio.Queue(maxsize=2)
                // We'll verify with a small custom test.
            } finally {
                collector.close();
            }
        }

        @Test
        @DisplayName("test_put_message_queue_full_triggers_cancel - with small queue via reflection")
        void testPutMessageQueueFullTriggersCancel_SmallQueue() throws Exception {
            ResponseCollector collector = new ResponseCollector("msg-full", "remote-full", null, 10.0);

            // Use reflection to replace the queue with a small one
            java.lang.reflect.Field queueField = ResponseCollector.class.getDeclaredField("queue");
            queueField.setAccessible(true);
            queueField.set(collector, new LinkedBlockingQueue<>(2));

            try {
                for (int i = 0; i < 3; i++) {
                    DmqResponseMessage msg = DmqResponseMessage.builder()
                        .messageId("msg-full")
                        .payload("data" + i)
                        .build();
                    collector.putMessage(msg);
                }

                assertTrue(collector.isCancelled());
            } finally {
                collector.close();
            }
        }
    }

    // ==================== Result ====================

    @Nested
    @DisplayName("TestResponseCollectorResult")
    class ResultTests {

        @Test
        @DisplayName("test_result_returns_payload - normal payload return")
        void testResultReturnsPayload() throws TimeoutException {
            ResponseCollector collector = new ResponseCollector("msg-result", "remote-result", null, 10.0);

            Map<String, String> payload = new HashMap<>();
            payload.put("result", "success");
            DmqResponseMessage msg = DmqResponseMessage.builder()
                .messageId("msg-result")
                .payload(payload)
                .lastChunk(true)
                .build();
            collector.putMessage(msg);

            Object result = collector.result(null);
            assertEquals(payload, result);
        }

        @Test
        @DisplayName("test_result_exception_handling - cancelled throws CancellationException")
        void testResultCancelledThrowsCancellationException() {
            ResponseCollector collector1 = new ResponseCollector("msg-cancelled", "remote-cancelled", null, 10.0);
            collector1.close();
            assertThrows(CancellationException.class, () -> collector1.result(null));
        }

        @Test
        @DisplayName("test_result_exception_handling - expired throws TimeoutException")
        void testResultExpiredThrowsTimeoutException() throws InterruptedException {
            ResponseCollector collector2 = new ResponseCollector("msg-expired", "remote-expired", null, 0.1);
            Thread.sleep(200);
            assertThrows(TimeoutException.class, () -> collector2.result(null));
            collector2.close();
        }

        @Test
        @DisplayName("test_result_exception_handling - wait timeout throws TimeoutException")
        void testResultWaitTimeoutThrowsTimeoutException() {
            ResponseCollector collector3 = new ResponseCollector("msg-wait-timeout", "remote-wait", null, 0.2);
            assertThrows(TimeoutException.class, () -> collector3.result(0.05));
            collector3.close();
        }

        @Test
        @DisplayName("test_result_handles_cancel_events - TTL_EXPIRE -> TimeoutException")
        void testResultHandlesTtlExpireCancelEvent() {
            ResponseCollector collector1 = new ResponseCollector("msg-ce1", "remote-ce1", null, 10.0);
            collector1.getQueue().offer(new CancelEvent(CancelReason.TTL_EXPIRE));
            assertThrows(TimeoutException.class, () -> collector1.result(null));
        }

        @Test
        @DisplayName("test_result_handles_cancel_events - QUEUE_FULL -> CancellationException")
        void testResultHandlesQueueFullCancelEvent() {
            ResponseCollector collector2 = new ResponseCollector("msg-ce2", "remote-ce2", null, 10.0);
            collector2.getQueue().offer(new CancelEvent(CancelReason.QUEUE_FULL));
            assertThrows(CancellationException.class, () -> collector2.result(null));
        }

        @Test
        @DisplayName("test_result_handles_cancel_events - RUNNER_STOPPED -> JiuWenBaseException")
        void testResultHandlesRunnerStoppedCancelEvent() {
            ResponseCollector collector3 = new ResponseCollector("msg-ce3", "remote-ce3", null, 10.0);
            collector3.getQueue().offer(new CancelEvent(CancelReason.RUNNER_STOPPED));
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> collector3.result(null));
            assertEquals(StatusCode.RUNNER_STOPPED.getCode(), ex.getErrorCode());
        }

        @Test
        @DisplayName("test_result_handles_error_response - ERROR ResultType throws JiuWenBaseException")
        void testResultHandlesErrorResponse() {
            ResponseCollector collector = new ResponseCollector("msg-err", "remote-err", null, 10.0);

            DmqResponseMessage errorMsg = DmqResponseMessage.builder()
                .messageId("msg-err")
                .resultType(ResultType.ERROR)
                .payload(new HashMap<>())
                .errorCode(500)
                .errorMsg("Internal error")
                .lastChunk(true)
                .build();
            collector.putMessage(errorMsg);

            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> collector.result(null));
            assertEquals(StatusCode.REMOTE_AGENT_PROCESS_ERROR.getCode(), ex.getErrorCode());
        }
    }

    // ==================== Stream ====================

    @Nested
    @DisplayName("TestResponseCollectorStream")
    class StreamTests {

        @Test
        @DisplayName("test_stream_yields_payloads_until_last_chunk")
        void testStreamYieldsPayloadsUntilLastChunk() throws TimeoutException {
            ResponseCollector collector = new ResponseCollector("msg-stream", "remote-stream", null, 10.0);

            Map<String, String> chunk1 = new HashMap<>();
            chunk1.put("chunk", "Hello");
            Map<String, String> chunk2 = new HashMap<>();
            chunk2.put("chunk", " World");

            DmqResponseMessage msg1 = DmqResponseMessage.builder()
                .messageId("msg-stream").seq(0).payload(chunk1).lastChunk(false).build();
            DmqResponseMessage msg2 = DmqResponseMessage.builder()
                .messageId("msg-stream").seq(1).payload(chunk2).lastChunk(false).build();
            DmqResponseMessage msg3 = DmqResponseMessage.builder()
                .messageId("msg-stream").seq(2).payload(new HashMap<>()).lastChunk(true).build();

            collector.putMessage(msg1);
            collector.putMessage(msg2);
            collector.putMessage(msg3);

            List<Object> results = collector.stream(null);

            assertEquals(2, results.size());
            assertEquals(chunk1, results.get(0));
            assertEquals(chunk2, results.get(1));
        }

        @Test
        @DisplayName("test_stream_exception_handling - timeout")
        void testStreamTimeoutThrowsTimeoutException() {
            ResponseCollector collector1 = new ResponseCollector("msg-stream-timeout", "remote-st", null, 0.2);
            assertThrows(TimeoutException.class, () -> collector1.stream(0.05));
            collector1.close();
        }

        @Test
        @DisplayName("test_stream_exception_handling - CancelEvent RUNNER_STOPPED")
        void testStreamCancelEventThrowsJiuWenBaseException() {
            ResponseCollector collector2 = new ResponseCollector("msg-stream-ce", "remote-sce", null, 10.0);
            collector2.getQueue().offer(new CancelEvent(CancelReason.RUNNER_STOPPED));
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> collector2.stream(null));
            assertEquals(StatusCode.RUNNER_STOPPED.getCode(), ex.getErrorCode());
        }

        @Test
        @DisplayName("test_stream_exception_handling - ERROR response")
        void testStreamErrorResponseThrowsJiuWenBaseException() {
            ResponseCollector collector3 = new ResponseCollector("msg-stream-err", "remote-serr", null, 10.0);
            DmqResponseMessage errorMsg = DmqResponseMessage.builder()
                .messageId("msg-stream-err")
                .resultType(ResultType.ERROR)
                .payload(new HashMap<>())
                .errorCode(503)
                .errorMsg("Service unavailable")
                .lastChunk(true)
                .build();
            collector3.putMessage(errorMsg);
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> collector3.stream(null));
            assertEquals(StatusCode.REMOTE_AGENT_PROCESS_ERROR.getCode(), ex.getErrorCode());
        }
    }

    // ==================== Close ====================

    @Nested
    @DisplayName("TestResponseCollectorClose")
    class CloseTests {

        @Test
        @DisplayName("test_close_lifecycle - full close lifecycle")
        void testCloseLifecycle() throws InterruptedException {
            ResponseCollector collector = new ResponseCollector("msg-close", "remote-close", null, 10.0);

            // Put a message
            DmqResponseMessage msg = DmqResponseMessage.builder()
                .messageId("msg-close")
                .payload("data")
                .build();
            collector.putMessage(msg);
            assertTrue(collector.getQueue().size() > 0);

            // Close
            collector.close(CancelReason.FINISH);
            Thread.sleep(50);

            // Verify state
            assertTrue(collector.isCancelled());
            assertEquals(0, collector.getQueue().size());
        }

        @Test
        @DisplayName("test_close_wakes_waiters - close wakes blocked result()")
        void testCloseWakesWaiters() throws Exception {
            ResponseCollector collector = new ResponseCollector("msg-close-wake", "remote-cw", null, 10.0);

            // Start a thread that blocks on result()
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return collector.result(null);
                } catch (TimeoutException e) {
                    throw new CompletionException(e);
                }
            });

            Thread.sleep(50); // Let the result() call start blocking

            collector.close(CancelReason.RUNNER_STOPPED);

            // The future should complete with an exception
            try {
                future.get(2, TimeUnit.SECONDS);
                fail("Expected an exception");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof JiuWenBaseException,
                    "Expected JiuWenBaseException but got: " + e.getCause().getClass().getName());
            }
        }

        @Test
        @DisplayName("test_close_is_idempotent")
        void testCloseIsIdempotent() {
            ResponseCollector collector = new ResponseCollector("msg-close-idem", "remote-ci", null, 10.0);
            collector.close();
            collector.close(); // Second close should not throw
            assertTrue(collector.isCancelled());
        }
    }
}

