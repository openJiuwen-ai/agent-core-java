/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamWriterManager stream output functionality.
 * 
 * <p>Converted from Python: test_stream_output.py</p>
 * <p>Python测试类: TestStreamOutput</p>
 * <p>Python测试方法数: 4</p>
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
    @DisplayName("stream output with custom writer")
    void testStreamOutputWithCustomWriter() throws ExecutionException, InterruptedException {
        // Python: mock_data = [{"name": "Alice", "age": 30}, {"name": "Bob", "age": 25}, ...]
        List<Map<String, Object>> mockData = List.of(
            Map.of("name", "Alice", "age", 30),
            Map.of("name", "Bob", "age", 25),
            Map.of("name", "Charlie", "age", 35)
        );
        
        List<Object> receivedData = new ArrayList<>();
        
        // Write data task
        CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
            try {
                for (Map<String, Object> data : mockData) {
                    CustomStreamWriter customWriter = manager.getCustomWriter();
                    customWriter.write(data).get();
                }
                emitter.close().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Read data task - streamOutput() returns Iterable<Object>
        CompletableFuture<Void> readTask = CompletableFuture.runAsync(() -> {
            for (Object data : manager.streamOutput(1, 1, false)) {
                receivedData.add(data);
            }
        });
        
        // Wait for both tasks
        CompletableFuture.allOf(writeTask, readTask).get();
        
        // Verify received data
        assertEquals(3, receivedData.size());
        for (Object data : receivedData) {
            assertInstanceOf(CustomSchema.class, data);
        }
    }
    
    @Test
    @DisplayName("stream output with output writer")
    void testStreamOutputWithOutputWriter() throws ExecutionException, InterruptedException {
        // Python: mock_data = [{"type": "nodeA", "index": 1, "payload": "nodeA_stream"}, ...]
        List<Map<String, Object>> mockData = List.of(
            Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"),
            Map.of("type", "nodeB", "index", 1, "payload", "nodeB_stream"),
            Map.of("type", "nodeC", "index", 1, "payload", "nodeC_stream")
        );
        
        List<Object> receivedData = new ArrayList<>();
        
        // Write data task
        CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
            try {
                for (Map<String, Object> data : mockData) {
                    OutputStreamWriter outputWriter = manager.getOutputWriter();
                    outputWriter.write(data).get();
                }
                emitter.close().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Read data task - streamOutput() returns Iterable<Object>
        CompletableFuture<Void> readTask = CompletableFuture.runAsync(() -> {
            for (Object data : manager.streamOutput(1, 1, false)) {
                receivedData.add(data);
            }
        });
        
        // Wait for both tasks
        CompletableFuture.allOf(writeTask, readTask).get();
        
        // Verify received data
        assertEquals(3, receivedData.size());
        for (Object data : receivedData) {
            assertInstanceOf(OutputSchema.class, data);
        }
        
        // Verify first item
        OutputSchema first = (OutputSchema) receivedData.get(0);
        assertEquals("nodeA", first.type());
        assertEquals(1, first.index());
        assertEquals("nodeA_stream", first.payload());
    }
    
    @Test
    @DisplayName("stream output with trace writer")
    void testStreamOutputWithTraceWriter() throws ExecutionException, InterruptedException {
        // Python: mock_data = [{"type": "on_chain_start", "payload": "nodeA_start"}, ...]
        List<Map<String, Object>> mockData = List.of(
            Map.of("type", "on_chain_start", "payload", "nodeA_start"),
            Map.of("type", "on_chain_end", "payload", "nodeA_end"),
            Map.of("type", "on_chain_error", "payload", "nodeA_error")
        );
        
        List<Object> receivedData = new ArrayList<>();
        
        // Write data task
        CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
            try {
                for (Map<String, Object> data : mockData) {
                    TraceStreamWriter traceWriter = manager.getTraceWriter();
                    traceWriter.write(data).get();
                }
                emitter.close().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Read data task - streamOutput() returns Iterable<Object>
        CompletableFuture<Void> readTask = CompletableFuture.runAsync(() -> {
            for (Object data : manager.streamOutput(1, 1, false)) {
                receivedData.add(data);
            }
        });
        
        // Wait for both tasks
        CompletableFuture.allOf(writeTask, readTask).get();
        
        // Verify received data
        assertEquals(3, receivedData.size());
        for (Object data : receivedData) {
            assertInstanceOf(TraceSchema.class, data);
        }
        
        // Verify first item
        TraceSchema first = (TraceSchema) receivedData.get(0);
        assertEquals("on_chain_start", first.type());
        assertEquals("nodeA_start", first.payload());
    }
    
    @Test
    @DisplayName("stream output with mock writer")
    void testStreamOutputWithMockWriter() throws ExecutionException, InterruptedException {
        // Create a mock schema class and writer
        // Python: class MockSchema(BaseModel): data: str
        // Python: class MockStreamWriter(StreamWriter[dict, MockSchema]): ...
        
        // Create mock writer with custom schema validation
        StreamWriter<Map<String, Object>, Map<String, Object>> mockWriter = 
            new StreamWriter<>(emitter, data -> {
                if (!data.containsKey("data")) {
                    throw new IllegalArgumentException("MockSchema requires data field");
                }
                return data;
            }, "MockSchema");
        
        List<Map<String, Object>> mockData = List.of(
            Map.of("data", "nodeA_stream"),
            Map.of("data", "nodeB_stream"),
            Map.of("data", "nodeC_stream")
        );
        
        List<Object> receivedData = new ArrayList<>();
        
        // Write data task
        CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
            try {
                for (Map<String, Object> data : mockData) {
                    mockWriter.write(data).get();
                }
                emitter.close().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Read data task - streamOutput() returns Iterable<Object>
        CompletableFuture<Void> readTask = CompletableFuture.runAsync(() -> {
            for (Object data : manager.streamOutput(1, 1, false)) {
                receivedData.add(data);
            }
        });
        
        // Wait for both tasks
        CompletableFuture.allOf(writeTask, readTask).get();
        
        // Verify received data
        assertEquals(3, receivedData.size());
        for (int i = 0; i < receivedData.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) receivedData.get(i);
            assertEquals(mockData.get(i).get("data"), data.get("data"));
        }
    }
}
