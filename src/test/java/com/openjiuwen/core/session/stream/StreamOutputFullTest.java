/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive tests for stream output subsystem: {@link StreamEmitter}, {@link StreamWriterManager},
 * {@link StreamWriter}, and stream schemas.
 * <p>
 * Ported from Python's {@code test_stream_output.py}.
 */
class StreamOutputFullTest {
    private StreamEmitter emitter;
    private StreamWriterManager manager;

    @BeforeEach
    void setUp() {
        emitter = new StreamEmitter();
        manager = new StreamWriterManager(emitter);
    }

    // ---------- Custom Writer tests ----------

    @Nested
    @DisplayName("Custom Writer")
    class CustomWriterTests {
        @Test
        @DisplayName("stream output with custom writer - producer/consumer pattern")
        void testCustomWriterProducerConsumer() throws Exception {
            List<Object> received = new ArrayList<>();

            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                StreamWriter<CustomSchema> writer = manager.getCustomWriter();
                writer.write(Map.of("name", "Alice", "age", 30));
                writer.write(Map.of("name", "Bob", "age", 25));
                writer.write(Map.of("name", "Charlie", "age", 35));
                emitter.close();
            });

            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> manager.streamOutput(received::add));

            CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
            assertEquals(3, received.size());

            // Verify all items are CustomSchema instances
            for (Object item : received) {
                assertTrue(item instanceof CustomSchema,
                        "Expected CustomSchema but got " + item.getClass().getSimpleName());
            }
        }

        @Test
        @DisplayName("custom schema property access")
        void testCustomSchemaProperties() {
            CustomSchema schema = CustomSchema.fromMap(Map.of("name", "Alice", "age", 30));
            assertEquals("Alice", schema.get("name"));
            assertEquals(30, schema.get("age"));
        }
    }

    // ---------- Output Writer tests ----------

    @Nested
    @DisplayName("Output Writer")
    class OutputWriterTests {
        @Test
        @DisplayName("stream output with output writer - validates schema")
        void testOutputWriter() throws Exception {
            List<Object> received = new ArrayList<>();

            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                StreamWriter<OutputSchema> writer = manager.getOutputWriter();
                writer.write(Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"));
                writer.write(Map.of("type", "nodeB", "index", 1, "payload", "nodeB_stream"));
                writer.write(Map.of("type", "nodeC", "index", 1, "payload", "nodeC_stream"));
                emitter.close();
            });

            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> manager.streamOutput(received::add));

            CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
            assertEquals(3, received.size());

            for (Object item : received) {
                assertTrue(item instanceof OutputSchema);
            }
            // Verify first item properties
            OutputSchema first = (OutputSchema) received.get(0);
            assertEquals("nodeA", first.getType());
            assertEquals(1, first.getIndex());
            assertEquals("nodeA_stream", first.getPayload());
        }
    }

    // ---------- Trace Writer tests ----------

    @Nested
    @DisplayName("Trace Writer")
    class TraceWriterTests {
        @Test
        @DisplayName("stream output with trace writer")
        void testTraceWriter() throws Exception {
            List<Object> received = new ArrayList<>();

            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                StreamWriter<TraceSchema> writer = manager.getTraceWriter();
                writer.write(Map.of("type", "on_chain_start", "payload", "nodeA_start"));
                writer.write(Map.of("type", "on_chain_end", "payload", "nodeA_end"));
                writer.write(Map.of("type", "on_chain_error", "payload", "nodeA_error"));
                emitter.close();
            });

            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> manager.streamOutput(received::add));

            CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
            assertEquals(3, received.size());

            for (Object item : received) {
                assertTrue(item instanceof TraceSchema);
            }
            TraceSchema first = (TraceSchema) received.get(0);
            assertEquals("on_chain_start", first.getType());
            assertEquals("nodeA_start", first.getPayload());
        }
    }

    // ---------- StreamEmitter tests ----------

    @Nested
    @DisplayName("StreamEmitter")
    class EmitterTests {
        @Test
        @DisplayName("emitter closed state")
        void testEmitterClosedState() {
            assertFalse(emitter.isClosed());
            emitter.close();
            assertTrue(emitter.isClosed());
        }

        @Test
        @DisplayName("double close is idempotent")
        void testDoubleClose() {
            emitter.close();
            assertDoesNotThrow(() -> emitter.close());
        }

        @Test
        @DisplayName("emit after close throws IllegalStateException")
        void testEmitAfterClose() {
            emitter.close();
            assertThrows(IllegalStateException.class, () -> emitter.emit("data"));
        }

        @Test
        @DisplayName("emit sends data to queue")
        void testEmitSendsToQueue() {
            emitter.emit("test-data");
            Object received = emitter.getStreamQueue().receive(1000);
            assertEquals("test-data", received);
        }
    }

    // ---------- StreamWriterManager tests ----------

    @Nested
    @DisplayName("StreamWriterManager")
    class ManagerTests {
        @Test
        @DisplayName("get default writers returns non-null")
        void testGetDefaultWriters() {
            assertNotNull(manager.getOutputWriter());
            assertNotNull(manager.getTraceWriter());
            assertNotNull(manager.getCustomWriter());
        }

        @Test
        @DisplayName("remove default writer throws exception")
        void testRemoveDefaultWriterThrows() {
            assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.OUTPUT));
            assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.TRACE));
            assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.CUSTOM));
        }

        @Test
        @DisplayName("collectStreamOutput collects all items")
        void testCollectStreamOutput() throws Exception {
            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                manager.getCustomWriter().write(Map.of("key", "value1"));
                manager.getCustomWriter().write(Map.of("key", "value2"));
                emitter.close();
            });

            CompletableFuture<List<Object>> consumer =
                CompletableFuture.supplyAsync(() -> manager.collectStreamOutput());

            producer.get(5, TimeUnit.SECONDS);
            List<Object> result = consumer.get(5, TimeUnit.SECONDS);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("null emitter throws IllegalArgumentException")
        void testNullEmitterThrows() {
            assertThrows(IllegalArgumentException.class, () -> new StreamWriterManager(null));
        }

        @Test
        @DisplayName("getStreamEmitter returns the correct emitter")
        void testGetStreamEmitter() {
            assertSame(emitter, manager.getStreamEmitter());
        }
    }

    // ---------- StreamWriter validation tests ----------

    @Nested
    @DisplayName("StreamWriter validation")
    class WriterValidation {
        @Test
        @DisplayName("write null throws error")
        void testWriteNullThrows() {
            StreamWriter<CustomSchema> writer = manager.getCustomWriter();
            assertThrows(Exception.class, () -> writer.write(null));
        }

        @Test
        @DisplayName("write accepts schema instance directly")
        void testWriteSchemaInstance() throws Exception {
            List<Object> received = new ArrayList<>();
            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> manager.streamOutput(received::add));

            CustomSchema schema = new CustomSchema(Map.of("key", "direct"));
            manager.getCustomWriter().write(schema);
            emitter.close();

            consumer.get(5, TimeUnit.SECONDS);
            assertEquals(1, received.size());
            assertTrue(received.get(0) instanceof CustomSchema);
        }
    }

    // ---------- AsyncStreamQueue tests ----------

    @Nested
    @DisplayName("AsyncStreamQueue")
    class QueueTests {
        @Test
        @DisplayName("queue send and receive")
        void testSendReceive() {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.send("data1");
            Object result = queue.receive(1000);
            assertEquals("data1", result);
        }

        @Test
        @DisplayName("queue closed state")
        void testQueueClosedState() {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            assertFalse(queue.isClosed());
            queue.close();
            assertTrue(queue.isClosed());
        }

        @Test
        @DisplayName("send to closed queue throws")
        void testSendToClosedQueue() {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            queue.close();
            assertThrows(IllegalStateException.class, () -> queue.send("data"));
        }

        @Test
        @DisplayName("receive timeout returns null")
        void testReceiveTimeout() {
            AsyncStreamQueue queue = new AsyncStreamQueue();
            Object result = queue.receive(100);
            assertNull(result);
        }

        @Test
        @DisplayName("bounded queue respects capacity")
        void testBoundedQueue() {
            AsyncStreamQueue queue = new AsyncStreamQueue(2);
            queue.send("a");
            queue.send("b");
            assertEquals("a", queue.receive(100));
            assertEquals("b", queue.receive(100));
        }
    }

    // ---------- Schema tests ----------

    @Nested
    @DisplayName("Stream schemas")
    class SchemaTests {
        @Test
        @DisplayName("OutputSchema fromMap")
        void testOutputSchemaFromMap() {
            OutputSchema schema =
                OutputSchema.fromMap(Map.of("type", "test_type", "index", 5, "payload", "test_payload"));
            assertEquals("test_type", schema.getType());
            assertEquals(5, schema.getIndex());
            assertEquals("test_payload", schema.getPayload());
        }

        @Test
        @DisplayName("TraceSchema fromMap")
        void testTraceSchemaFromMap() {
            TraceSchema schema = TraceSchema.fromMap(Map.of("type", "on_chain_start", "payload", "test_payload"));
            assertEquals("on_chain_start", schema.getType());
            assertEquals("test_payload", schema.getPayload());
        }

        @Test
        @DisplayName("CustomSchema fromMap and property access")
        void testCustomSchemaFromMap() {
            CustomSchema schema = CustomSchema.fromMap(Map.of("key1", "value1", "key2", 42));
            assertEquals("value1", schema.get("key1"));
            assertEquals(42, schema.get("key2"));
            assertNull(schema.get("non_existent"));
        }

        @Test
        @DisplayName("OutputSchema fromMap with null throws")
        void testOutputSchemaFromMapNull() {
            assertThrows(IllegalArgumentException.class, () -> OutputSchema.fromMap(null));
        }

        @Test
        @DisplayName("TraceSchema fromMap with null throws")
        void testTraceSchemaFromMapNull() {
            assertThrows(IllegalArgumentException.class, () -> TraceSchema.fromMap(null));
        }

        @Test
        @DisplayName("StreamMode enum values")
        void testStreamModeValues() {
            assertEquals("output", StreamMode.OUTPUT.getMode());
            assertEquals("trace", StreamMode.TRACE.getMode());
            assertEquals("custom", StreamMode.CUSTOM.getMode());
        }
    }
}
