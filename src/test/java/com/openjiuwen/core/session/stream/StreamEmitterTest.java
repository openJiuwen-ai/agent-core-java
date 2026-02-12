/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AsyncStreamQueue and StreamEmitter.
 * 
 * <p>Converted from Python: test_emitter.py</p>
 */
class StreamEmitterTest {
    
    @Nested
    @DisplayName("AsyncStreamQueue Tests")
    class AsyncStreamQueueTests {
        
        @Test
        @DisplayName("construction with valid maxsize")
        void testConstructionWithValidMaxsize() {
            AsyncStreamQueue queue = new AsyncStreamQueue(10);
            assertFalse(queue.isClosed());
        }
        
        @Test
        @DisplayName("construction with zero maxsize (unbounded)")
        void testConstructionWithZeroMaxsize() {
            AsyncStreamQueue queue = new AsyncStreamQueue(0);
            assertFalse(queue.isClosed());
        }
        
        @Test
        @DisplayName("construction with negative maxsize raises")
        void testConstructionWithNegativeMaxsizeRaises() {
            assertThrows(IllegalArgumentException.class, () -> {
                new AsyncStreamQueue(-1);
            });
        }
        
        @Test
        @DisplayName("send and receive basic")
        void testSendAndReceiveBasic() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            Map<String, Object> testData = Map.of("key", "value");
            
            queue.send(testData).get();
            Object received = queue.receive().get();
            
            assertEquals(testData, received);
        }
        
        @Test
        @DisplayName("send multiple items")
        void testSendMultipleItems() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            List<Integer> items = List.of(1, 2, 3, 4, 5);
            
            for (Integer item : items) {
                queue.send(item).get();
            }
            
            List<Object> received = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                received.add(queue.receive().get());
            }
            
            assertEquals(items, received);
        }
        
        @Test
        @DisplayName("send after close raises")
        void testSendAfterCloseRaises() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.close().get();
            
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                queue.send("data").get();
            });
            assertTrue(ex.getCause().getMessage().contains("closed"));
        }
        
        @Test
        @DisplayName("receive after close raises")
        void testReceiveAfterCloseRaises() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.close().get();
            
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                queue.receive().get();
            });
            assertTrue(ex.getCause().getMessage().contains("closed"));
        }
        
        @Test
        @DisplayName("receive with timeout")
        void testReceiveWithTimeout() {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                queue.receive(0.1).get();
            });
            assertTrue(ex.getCause().getCause() instanceof TimeoutException);
        }
        
        @Test
        @DisplayName("is closed property")
        void testIsClosedProperty() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            assertFalse(queue.isClosed());
            
            queue.close().get();
            assertTrue(queue.isClosed());
        }
        
        @Test
        @DisplayName("close idempotent")
        void testCloseIdempotent() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.close().get();
            queue.close().get(); // Should not raise
            assertTrue(queue.isClosed());
        }
        
        @Test
        @DisplayName("send retries on timeout")
        void testSendRetriesOnTimeout() throws ExecutionException, InterruptedException {
            // Python: queue = AsyncStreamQueue(maxsize=1)
            //         await queue.send("first")
            //         asyncio.create_task(consume_later())
            //         await queue.send("second", attempt_timeout=0.05, max_retries=5)
            //         received = await queue.receive()
            //         assert received == "second"
            AsyncStreamQueue queue = new AsyncStreamQueue(1);
            
            // Fill the queue
            queue.send("first").get();
            
            // Start a consumer that will consume after a short delay
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100); // Wait 100ms
                    queue.receive().get(); // Consume "first"
                } catch (Exception e) {
                    // ignore
                }
            });
            
            // Second send should retry and eventually succeed (after first is consumed)
            queue.send("second", 0.05, 5).get();
            
            // Should have consumed "first" and sent "second" successfully
            Object received = queue.receive().get();
            assertEquals("second", received);
        }
        
        @Test
        @DisplayName("close waits for pending items")
        void testCloseWaitsForPendingItems() throws ExecutionException, InterruptedException {
            // Python: queue = AsyncStreamQueue()
            //         await queue.send("data")
            //         asyncio.create_task(consume())
            //         await queue.close(timeout=1.0)  # Should wait for consume
            //         assert queue.is_closed
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.send("data").get();
            
            // Start a consumer that will consume after a short delay
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50); // Wait 50ms
                    queue.receive().get();
                } catch (Exception e) {
                    // ignore
                }
            });
            
            // Close should wait for the consumer to finish
            queue.close(1.0).get();
            assertTrue(queue.isClosed());
        }
        
        @Test
        @DisplayName("close force clears on timeout")
        void testCloseForceClears() throws ExecutionException, InterruptedException {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.send("data1").get();
            queue.send("data2").get();
            
            // Close with very short timeout should force clear
            queue.close(0.01).get();
            assertTrue(queue.isClosed());
        }
    }
    
    @Nested
    @DisplayName("StreamEmitter Tests")
    class StreamEmitterTests {
        
        private StreamEmitter emitter;
        
        @BeforeEach
        void setUp() {
            emitter = new StreamEmitter();
        }
        
        @Test
        @DisplayName("emit basic")
        void testEmitBasic() throws ExecutionException, InterruptedException {
            Map<String, Object> testData = Map.of("message", "hello");
            emitter.emit(testData).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertEquals(testData, received);
        }
        
        @Test
        @DisplayName("emit multiple items")
        void testEmitMultipleItems() throws ExecutionException, InterruptedException {
            List<String> items = List.of("item1", "item2", "item3");
            for (String item : items) {
                emitter.emit(item).get();
            }
            
            List<Object> received = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                received.add(emitter.getStreamQueue().receive().get());
            }
            assertEquals(items, received);
        }
        
        @Test
        @DisplayName("emit after close raises")
        void testEmitAfterCloseRaises() throws ExecutionException, InterruptedException {
            emitter.close().get();
            
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                emitter.emit("data").get();
            });
            assertTrue(ex.getCause().getMessage().contains("closed"));
        }
        
        @Test
        @DisplayName("is closed reflects state")
        void testIsClosedReflectsState() throws ExecutionException, InterruptedException {
            assertFalse(emitter.isClosed());
            emitter.close().get();
            assertTrue(emitter.isClosed());
        }
        
        @Test
        @DisplayName("close sends end frame")
        void testCloseSendsEndFrame() throws ExecutionException, InterruptedException {
            emitter.close().get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertEquals(StreamEmitter.END_FRAME, received);
        }
        
        @Test
        @DisplayName("close idempotent")
        void testCloseIdempotent() throws ExecutionException, InterruptedException {
            emitter.close().get();
            emitter.close().get(); // Should not raise or send duplicate END_FRAME
            assertTrue(emitter.isClosed());
        }
        
        @Test
        @DisplayName("stream queue property")
        void testStreamQueueProperty() {
            assertInstanceOf(AsyncStreamQueue.class, emitter.getStreamQueue());
        }
        
        @Test
        @DisplayName("end frame constant")
        void testEndFrameConstant() {
            assertEquals("all streaming outputs finish", StreamEmitter.END_FRAME);
        }
    }
}

