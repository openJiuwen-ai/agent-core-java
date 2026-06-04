/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamOutput.
 * Mirrors Python's {@code tests/unit_tests/core/session/stream/test_stream_output.py}.
 */
class TestStreamOutput {

    private StreamEmitter emitter;
    private StreamWriterManager manager;

    @BeforeEach
    void setUp() {
        emitter = new StreamEmitter();
        manager = new StreamWriterManager(emitter);
    }

    @Nested
    @DisplayName("StreamOutput with custom writer tests")
    class StreamOutputWithCustomWriterTests {

        @Test
        @DisplayName("test stream output with custom writer")
        void testStreamOutputWithCustomWriter() throws Exception {
            List<Map<String, Object>> mockData = new ArrayList<>();
            mockData.add(createMap("name", "Alice", "age", 30));
            mockData.add(createMap("name", "Bob", "age", 25));
            mockData.add(createMap("name", "Charlie", "age", 35));

            List<Object> receivedData = new ArrayList<>();
            CountDownLatch writeLatch = new CountDownLatch(1);
            CountDownLatch readLatch = new CountDownLatch(1);
            AtomicInteger writeIndex = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    StreamWriter<CustomSchema> customWriter = manager.getCustomWriter();
                    for (Map<String, Object> data : mockData) {
                        customWriter.write(data);
                    }
                    emitter.close();
                    writeLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try {
                    manager.streamOutput(item -> {
                        receivedData.add(item);
                    });
                    readLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            writeLatch.await(5, TimeUnit.SECONDS);
            readLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(3, receivedData.size());
        }
    }

    @Nested
    @DisplayName("StreamOutput with output writer tests")
    class StreamOutputWithOutputWriterTests {

        @Test
        @DisplayName("test stream output with output writer")
        void testStreamOutputWithOutputWriter() throws Exception {
            List<Map<String, Object>> mockData = new ArrayList<>();
            mockData.add(createOutputMap("nodeA", 1, "nodeA_stream"));
            mockData.add(createOutputMap("nodeB", 1, "nodeB_stream"));
            mockData.add(createOutputMap("nodeC", 1, "nodeC_stream"));

            List<Object> receivedData = new ArrayList<>();
            CountDownLatch writeLatch = new CountDownLatch(1);
            CountDownLatch readLatch = new CountDownLatch(1);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    StreamWriter<OutputSchema> outputWriter = manager.getOutputWriter();
                    for (Map<String, Object> data : mockData) {
                        outputWriter.write(data);
                    }
                    emitter.close();
                    writeLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try {
                    manager.streamOutput(item -> {
                        receivedData.add(item);
                    });
                    readLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            writeLatch.await(5, TimeUnit.SECONDS);
            readLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(3, receivedData.size());
            for (Object item : receivedData) {
                assertTrue(item instanceof OutputSchema);
            }
        }
    }

    @Nested
    @DisplayName("StreamOutput with trace writer tests")
    class StreamOutputWithTraceWriterTests {

        @Test
        @DisplayName("test stream output with trace writer")
        void testStreamOutputWithTraceWriter() throws Exception {
            List<Map<String, Object>> mockData = new ArrayList<>();
            mockData.add(createTraceMap("on_chain_start", "nodeA_start"));
            mockData.add(createTraceMap("on_chain_end", "nodeA_end"));
            mockData.add(createTraceMap("on_chain_error", "nodeA_error"));

            List<Object> receivedData = new ArrayList<>();
            CountDownLatch writeLatch = new CountDownLatch(1);
            CountDownLatch readLatch = new CountDownLatch(1);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    StreamWriter<TraceSchema> traceWriter = manager.getTraceWriter();
                    for (Map<String, Object> data : mockData) {
                        traceWriter.write(data);
                    }
                    emitter.close();
                    writeLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try {
                    manager.streamOutput(item -> {
                        receivedData.add(item);
                    });
                    readLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            writeLatch.await(5, TimeUnit.SECONDS);
            readLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(3, receivedData.size());
            for (Object item : receivedData) {
                assertTrue(item instanceof TraceSchema);
            }
        }
    }

    @Nested
    @DisplayName("StreamOutput with mock writer tests")
    class StreamOutputWithMockWriterTests {

        @Test
        @DisplayName("test stream output with mock writer")
        void testStreamOutputWithMockWriter() throws Exception {
            StreamWriter<CustomSchema> mockWriter = new StreamWriter<>(
                emitter,
                CustomSchema.class,
                CustomSchema::fromMap
            );

            StreamMode mockMode = StreamMode.CUSTOM;
            manager.addWriter(mockMode, mockWriter);

            List<Map<String, Object>> mockData = new ArrayList<>();
            mockData.add(createMockDataMap("nodeA_stream"));
            mockData.add(createMockDataMap("nodeB_stream"));
            mockData.add(createMockDataMap("nodeC_stream"));

            List<Object> receivedData = new ArrayList<>();
            CountDownLatch writeLatch = new CountDownLatch(1);
            CountDownLatch readLatch = new CountDownLatch(1);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    StreamWriter<?> writer = manager.getWriter(mockMode);
                    for (Map<String, Object> data : mockData) {
                        writer.write(data);
                    }
                    emitter.close();
                    writeLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try {
                    manager.streamOutput(item -> {
                        receivedData.add(item);
                    });
                    readLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            writeLatch.await(5, TimeUnit.SECONDS);
            readLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(3, receivedData.size());
        }
    }

    private Map<String, Object> createMap(String nameKey, Object nameValue, String ageKey, Object ageValue) {
        Map<String, Object> map = new HashMap<>();
        map.put(nameKey, nameValue);
        map.put(ageKey, ageValue);
        return map;
    }

    private Map<String, Object> createOutputMap(String type, int index, Object payload) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("index", index);
        map.put("payload", payload);
        return map;
    }

    private Map<String, Object> createTraceMap(String type, Object payload) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("payload", payload);
        return map;
    }

    private Map<String, Object> createMockDataMap(String data) {
        Map<String, Object> map = new HashMap<>();
        map.put("data", data);
        return map;
    }
}
