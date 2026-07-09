/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests for stream output: {@link StreamEmitter}, {@link StreamWriterManager}, {@link StreamWriter}.
 * <p>
 * Ported from Python's {@code test_stream_output.py}.
 */
class StreamOutputTest {
    private StreamEmitter emitter;
    private StreamWriterManager manager;

    @BeforeEach
    void setUp() {
        emitter = new StreamEmitter();
        manager = new StreamWriterManager(emitter);
    }

    @Test
    @DisplayName("stream output with custom writer - producer/consumer")
    void testStreamOutputWithCustomWriter() throws Exception {
        List<Object> received = new ArrayList<>();

        // Producer: write data, then close
        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> mockData = List.of(Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Bob", "age", 25), Map.of("name", "Charlie", "age", 35));
            StreamWriter<CustomSchema> customWriter = manager.getCustomWriter();
            for (Map<String, Object> data : mockData) {
                customWriter.write(data);
            }
            emitter.close();
        });

        // Consumer: collect all stream data
        CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
            manager.streamOutput(received::add);
        });

        CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
        assertEquals(3, received.size());
    }

    @Test
    @DisplayName("stream output with output writer")
    void testStreamOutputWithOutputWriter() throws Exception {
        List<Object> received = new ArrayList<>();

        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> mockData = List.of(Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"),
                    Map.of("type", "nodeB", "index", 1, "payload", "nodeB_stream"),
                    Map.of("type", "nodeC", "index", 1, "payload", "nodeC_stream"));
            StreamWriter<OutputSchema> outputWriter = manager.getOutputWriter();
            for (Map<String, Object> data : mockData) {
                outputWriter.write(data);
            }
            emitter.close();
        });

        CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
            manager.streamOutput(received::add);
        });

        CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
        assertEquals(3, received.size());
        // Verify schema types
        for (Object item : received) {
            assertTrue(item instanceof OutputSchema);
        }
    }

    @Test
    @DisplayName("stream output with trace writer")
    void testStreamOutputWithTraceWriter() throws Exception {
        List<Object> received = new ArrayList<>();

        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> mockData = List.of(Map.of("type", "on_chain_start", "payload", "nodeA_start"),
                    Map.of("type", "on_chain_end", "payload", "nodeA_end"),
                    Map.of("type", "on_chain_error", "payload", "nodeA_error"));
            StreamWriter<TraceSchema> traceWriter = manager.getTraceWriter();
            for (Map<String, Object> data : mockData) {
                traceWriter.write(data);
            }
            emitter.close();
        });

        CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
            manager.streamOutput(received::add);
        });

        CompletableFuture.allOf(producer, consumer).get(5, TimeUnit.SECONDS);
        assertEquals(3, received.size());
        for (Object item : received) {
            assertTrue(item instanceof TraceSchema);
        }
    }

    @Test
    @DisplayName("collectStreamOutput collects all items")
    void testCollectStreamOutput() throws Exception {
        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            StreamWriter<CustomSchema> customWriter = manager.getCustomWriter();
            customWriter.write(Map.of("key", "value1"));
            customWriter.write(Map.of("key", "value2"));
            emitter.close();
        });

        CompletableFuture<List<Object>> consumer = CompletableFuture.supplyAsync(() -> manager.collectStreamOutput());

        producer.get(5, TimeUnit.SECONDS);
        List<Object> result = consumer.get(5, TimeUnit.SECONDS);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("emitter cannot emit after close")
    void testEmitterCannotEmitAfterClose() {
        emitter.close();
        assertTrue(emitter.isClosed());
        assertThrows(IllegalStateException.class, () -> emitter.emit("data"));
    }

    @Test
    @DisplayName("writer discards data if emitter is closed")
    void testWriterAfterEmitterClosed() {
        emitter.close();
        StreamWriter<CustomSchema> customWriter = manager.getCustomWriter();
        assertDoesNotThrow(() -> customWriter.write(Map.of("key", "discarded")));
        assertEquals(StreamEmitter.END_FRAME, emitter.getStreamQueue().receive(100));
    }

    @Test
    @DisplayName("streamIterator yields items incrementally")
    void testStreamIterator() throws Exception {
        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            StreamWriter<CustomSchema> customWriter = manager.getCustomWriter();
            customWriter.write(Map.of("key", "value1"));
            customWriter.write(Map.of("key", "value2"));
            emitter.close();
        });

        Iterator<Object> iterator = manager.streamIterator();
        List<Object> received = new ArrayList<>();
        while (iterator.hasNext()) {
            received.add(iterator.next());
        }

        producer.get(5, TimeUnit.SECONDS);
        assertEquals(2, received.size());
        assertTrue(received.get(0) instanceof CustomSchema);
    }

    @Test
    @DisplayName("removing default writer throws error")
    void testRemoveDefaultWriterThrows() {
        assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.OUTPUT));
        assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.TRACE));
        assertThrows(Exception.class, () -> manager.removeWriter(StreamMode.CUSTOM));
    }

    @Test
    @DisplayName("get writers returns correct types")
    void testGetWriters() {
        assertNotNull(manager.getOutputWriter());
        assertNotNull(manager.getTraceWriter());
        assertNotNull(manager.getCustomWriter());
    }
}
