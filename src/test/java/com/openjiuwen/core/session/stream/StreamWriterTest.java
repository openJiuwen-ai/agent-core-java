/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamWriter, OutputStreamWriter, TraceStreamWriter, and CustomStreamWriter.
 * 
 * <p>Converted from Python: test_writer.py</p>
 * <p>Python测试类数: 4</p>
 * <p>Python测试方法数: 14</p>
 */
class StreamWriterTest {
    
    @Nested
    @DisplayName("StreamWriter Tests")
    class StreamWriterTests {
        
        private StreamEmitter emitter;
        
        @BeforeEach
        void setUp() {
            emitter = new StreamEmitter();
        }
        
        @Test
        @DisplayName("construction with null emitter raises")
        void testConstructionWithNullEmitterRaises() {
            // Python: with pytest.raises(ValueError, match="stream_emitter can not be None"):
            //             StreamWriter(None, OutputSchema)
            assertThrows(IllegalArgumentException.class, () -> {
                new OutputStreamWriter(null);
            });
        }
        
        @Test
        @DisplayName("construction with valid params")
        void testConstructionWithValidParams() {
            // Python: writer = StreamWriter(emitter, OutputSchema)
            //         assert writer._stream_emitter is emitter
            //         assert writer._schema_type is OutputSchema
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            assertSame(emitter, writer.getStreamEmitter());
            assertEquals("OutputSchema", writer.getSchemaTypeName());
        }
        
        @Test
        @DisplayName("write null raises exception")
        void testWriteNullRaisesException() {
            // Python: with pytest.raises(JiuWenBaseException) as exc_info:
            //             await writer.write(None)
            //         assert "can not write None" in str(exc_info.value)
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                writer.write(null).get();
            });
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("can not write None"));
        }
        
        @Test
        @DisplayName("write invalid schema raises exception")
        void testWriteInvalidSchemaRaisesException() {
            // Python: invalid_data = {"invalid": "data"}
            //         with pytest.raises(JiuWenBaseException) as exc_info:
            //             await writer.write(invalid_data)
            //         assert "Data validation failed" in str(exc_info.value)
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            Map<String, Object> invalidData = Map.of("invalid", "data");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                writer.write(invalidData).get();
            });
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Data validation failed"));
        }
        
        @Test
        @DisplayName("write valid data emits")
        void testWriteValidDataEmits() throws ExecutionException, InterruptedException {
            // Python: valid_data = {"type": "output", "index": 0, "payload": "test"}
            //         await writer.write(valid_data)
            //         received = await emitter.stream_queue.receive()
            //         assert received.type == "output"
            //         assert received.index == 0
            //         assert received.payload == "test"
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            Map<String, Object> validData = new HashMap<>();
            validData.put("type", "output");
            validData.put("index", 0);
            validData.put("payload", "test");
            writer.write(validData).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertInstanceOf(OutputSchema.class, received);
            OutputSchema schema = (OutputSchema) received;
            assertEquals("output", schema.type());
            assertEquals(0, schema.index());
            assertEquals("test", schema.payload());
        }
        
        @Test
        @DisplayName("write after emitter closed discards")
        void testWriteAfterEmitterClosedDiscards() throws ExecutionException, InterruptedException {
            // Python: await emitter.close()
            //         valid_data = {"type": "output", "index": 0, "payload": "test"}
            //         await writer.write(valid_data)
            //         # No exception means success (message was discarded)
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            emitter.close().get();
            
            // Should not raise, just log warning and discard
            Map<String, Object> validData = new HashMap<>();
            validData.put("type", "output");
            validData.put("index", 0);
            validData.put("payload", "test");
            
            // This should complete without exception (message discarded)
            assertDoesNotThrow(() -> writer.write(validData).get());
        }
    }
    
    @Nested
    @DisplayName("OutputStreamWriter Tests")
    class OutputStreamWriterTests {
        
        private StreamEmitter emitter;
        
        @BeforeEach
        void setUp() {
            emitter = new StreamEmitter();
        }
        
        @Test
        @DisplayName("default schema type is OutputSchema")
        void testDefaultSchemaType() {
            // Python: assert writer._schema_type is OutputSchema
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            assertEquals("OutputSchema", writer.getSchemaTypeName());
        }
        
        @Test
        @DisplayName("write output schema")
        void testWriteOutputSchema() throws ExecutionException, InterruptedException {
            // Python: data = {"type": "text", "index": 1, "payload": {"content": "hello"}}
            //         await writer.write(data)
            //         received = await emitter.stream_queue.receive()
            //         assert received.type == "text"
            //         assert received.index == 1
            //         assert received.payload == {"content": "hello"}
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "text");
            data.put("index", 1);
            data.put("payload", Map.of("content", "hello"));
            writer.write(data).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertInstanceOf(OutputSchema.class, received);
            OutputSchema schema = (OutputSchema) received;
            assertEquals("text", schema.type());
            assertEquals(1, schema.index());
            assertEquals(Map.of("content", "hello"), schema.payload());
        }
        
        @Test
        @DisplayName("write output schema missing fields raises")
        void testWriteOutputSchemaMissingFieldsRaises() {
            // Python: data = {"type": "text", "payload": "test"}
            //         with pytest.raises(JiuWenBaseException):
            //             await writer.write(data)
            OutputStreamWriter writer = new OutputStreamWriter(emitter);
            // Missing 'index' field
            Map<String, Object> data = Map.of("type", "text", "payload", "test");
            assertThrows(ExecutionException.class, () -> writer.write(data).get());
        }
    }
    
    @Nested
    @DisplayName("TraceStreamWriter Tests")
    class TraceStreamWriterTests {
        
        private StreamEmitter emitter;
        
        @BeforeEach
        void setUp() {
            emitter = new StreamEmitter();
        }
        
        @Test
        @DisplayName("default schema type is TraceSchema")
        void testDefaultSchemaType() {
            // Python: assert writer._schema_type is TraceSchema
            TraceStreamWriter writer = new TraceStreamWriter(emitter);
            assertEquals("TraceSchema", writer.getSchemaTypeName());
        }
        
        @Test
        @DisplayName("write trace schema")
        void testWriteTraceSchema() throws ExecutionException, InterruptedException {
            // Python: data = {"type": "span", "payload": {"span_id": "123", "name": "test_span"}}
            //         await writer.write(data)
            //         received = await emitter.stream_queue.receive()
            //         assert received.type == "span"
            //         assert received.payload == {"span_id": "123", "name": "test_span"}
            TraceStreamWriter writer = new TraceStreamWriter(emitter);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "span");
            data.put("payload", Map.of("span_id", "123", "name", "test_span"));
            writer.write(data).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertInstanceOf(TraceSchema.class, received);
            TraceSchema schema = (TraceSchema) received;
            assertEquals("span", schema.type());
            assertEquals(Map.of("span_id", "123", "name", "test_span"), schema.payload());
        }
        
        @Test
        @DisplayName("write trace schema missing fields raises")
        void testWriteTraceSchemaMissingFieldsRaises() {
            // Python: data = {"type": "span"}
            //         with pytest.raises(JiuWenBaseException):
            //             await writer.write(data)
            TraceStreamWriter writer = new TraceStreamWriter(emitter);
            // Missing 'payload' field
            Map<String, Object> data = Map.of("type", "span");
            assertThrows(ExecutionException.class, () -> writer.write(data).get());
        }
    }
    
    @Nested
    @DisplayName("CustomStreamWriter Tests")
    class CustomStreamWriterTests {
        
        private StreamEmitter emitter;
        
        @BeforeEach
        void setUp() {
            emitter = new StreamEmitter();
        }
        
        @Test
        @DisplayName("default schema type is CustomSchema")
        void testDefaultSchemaType() {
            // Python: assert writer._schema_type is CustomSchema
            CustomStreamWriter writer = new CustomStreamWriter(emitter);
            assertEquals("CustomSchema", writer.getSchemaTypeName());
        }
        
        @Test
        @DisplayName("write custom schema")
        void testWriteCustomSchema() throws ExecutionException, InterruptedException {
            // Python: data = {"custom_field": "value", "number": 42, "nested": {"a": 1}}
            //         await writer.write(data)
            //         received = await emitter.stream_queue.receive()
            //         assert received.custom_field == "value"
            //         assert received.number == 42
            //         assert received.nested == {"a": 1}
            CustomStreamWriter writer = new CustomStreamWriter(emitter);
            Map<String, Object> data = new HashMap<>();
            data.put("custom_field", "value");
            data.put("number", 42);
            data.put("nested", Map.of("a", 1));
            writer.write(data).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertInstanceOf(CustomSchema.class, received);
            CustomSchema schema = (CustomSchema) received;
            assertEquals("value", schema.get("custom_field"));
            assertEquals(42, schema.get("number"));
            assertEquals(Map.of("a", 1), schema.get("nested"));
        }
        
        @Test
        @DisplayName("write custom schema empty dict")
        void testWriteCustomSchemaEmptyDict() throws ExecutionException, InterruptedException {
            // Python: data = {}
            //         await writer.write(data)
            //         received = await emitter.stream_queue.receive()
            //         assert isinstance(received, CustomSchema)
            CustomStreamWriter writer = new CustomStreamWriter(emitter);
            Map<String, Object> data = new HashMap<>();
            writer.write(data).get();
            
            Object received = emitter.getStreamQueue().receive().get();
            assertInstanceOf(CustomSchema.class, received);
        }
    }
}









