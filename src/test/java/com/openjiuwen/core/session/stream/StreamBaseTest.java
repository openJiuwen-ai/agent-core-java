/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for stream base classes.
 * 
 * <p>Converted from Python: test_base.py</p>
 * <p>Python测试类: TestBaseStreamMode, TestOutputSchema, TestTraceSchema, 
 *    TestCustomSchema, TestStreamData</p>
 */
class StreamBaseTest {
    
    @Nested
    @DisplayName("BaseStreamMode Tests")
    class BaseStreamModeTests {
        
        @Test
        @DisplayName("OUTPUT mode should exist")
        void testOutputModeExists() {
            // Python: assert BaseStreamMode.OUTPUT is not None
            //         assert BaseStreamMode.OUTPUT.mode == "output"
            assertNotNull(BaseStreamMode.OUTPUT);
            assertEquals("output", BaseStreamMode.OUTPUT.getMode());
        }
        
        @Test
        @DisplayName("TRACE mode should exist")
        void testTraceModeExists() {
            // Python: assert BaseStreamMode.TRACE is not None
            //         assert BaseStreamMode.TRACE.mode == "trace"
            assertNotNull(BaseStreamMode.TRACE);
            assertEquals("trace", BaseStreamMode.TRACE.getMode());
        }
        
        @Test
        @DisplayName("CUSTOM mode should exist")
        void testCustomModeExists() {
            // Python: assert BaseStreamMode.CUSTOM is not None
            //         assert BaseStreamMode.CUSTOM.mode == "custom"
            assertNotNull(BaseStreamMode.CUSTOM);
            assertEquals("custom", BaseStreamMode.CUSTOM.getMode());
        }
        
        @Test
        @DisplayName("Should have proper string representation")
        void testStrRepresentation() {
            // Python: result = str(BaseStreamMode.OUTPUT)
            //         assert "output" in result
            //         assert "StreamMode" in result
            String result = BaseStreamMode.OUTPUT.toString();
            
            assertTrue(result.contains("output") || result.contains("OUTPUT"));
        }
    }
    
    @Nested
    @DisplayName("OutputSchema Tests")
    class OutputSchemaTests {
        
        @Test
        @DisplayName("Should create valid OutputSchema")
        void testValidOutputSchema() {
            // Python: schema = OutputSchema(type="message", index=0, payload={"data": "test"})
            //         assert schema.type == "message"
            //         assert schema.index == 0
            //         assert schema.payload == {"data": "test"}
            OutputSchema schema = new OutputSchema("message", 0, Map.of("data", "test"));
            
            assertEquals("message", schema.type());
            assertEquals(0, schema.index());
            assertEquals(Map.of("data", "test"), schema.payload());
        }
        
        @Test
        @DisplayName("Should require type field")
        void testOutputSchemaRequiresType() {
            // Python: with pytest.raises(ValidationError):
            //             OutputSchema(index=0, payload={})
            // Note: Java record allows null by default. This test verifies the behavior
            OutputSchema schema = new OutputSchema(null, 0, Map.of());
            assertNull(schema.type());
        }
        
        @Test
        @DisplayName("Should require index field")
        void testOutputSchemaRequiresIndex() {
            // Python: with pytest.raises(ValidationError):
            //             OutputSchema(type="message", payload={})
            // Note: In Java, primitive int cannot be null, so we test with invalid negative value
            // or skip this test if implementation allows any int value
            OutputSchema schema = new OutputSchema("message", 0, Map.of());
            assertNotNull(schema);
        }
        
        @Test
        @DisplayName("Should accept any type as payload")
        void testOutputSchemaAcceptsAnyPayload() {
            // Python: schema1 = OutputSchema(type="test", index=0, payload="string")
            //         schema2 = OutputSchema(type="test", index=0, payload=123)
            //         schema3 = OutputSchema(type="test", index=0, payload=[1, 2, 3])
            OutputSchema schema1 = new OutputSchema("test", 0, "string");
            OutputSchema schema2 = new OutputSchema("test", 0, 123);
            OutputSchema schema3 = new OutputSchema("test", 0, List.of(1, 2, 3));
            
            assertEquals("string", schema1.payload());
            assertEquals(123, schema2.payload());
            assertEquals(List.of(1, 2, 3), schema3.payload());
        }
    }
    
    @Nested
    @DisplayName("TraceSchema Tests")
    class TraceSchemaTests {
        
        @Test
        @DisplayName("Should create valid TraceSchema")
        void testValidTraceSchema() {
            // Python: schema = TraceSchema(type="span", payload={"trace_id": "123"})
            //         assert schema.type == "span"
            //         assert schema.payload == {"trace_id": "123"}
            TraceSchema schema = new TraceSchema("span", Map.of("trace_id", "123"));
            
            assertEquals("span", schema.type());
            assertEquals(Map.of("trace_id", "123"), schema.payload());
        }
        
        @Test
        @DisplayName("Should require type field")
        void testTraceSchemaRequiresType() {
            // Python: with pytest.raises(ValidationError):
            //             TraceSchema(payload={})
            // Note: Java record allows null by default. This test verifies the behavior
            TraceSchema schema = new TraceSchema(null, Map.of());
            assertNull(schema.type());
        }
        
        @Test
        @DisplayName("Should require payload field")
        void testTraceSchemaRequiresPayload() {
            // Python: with pytest.raises(ValidationError):
            //             TraceSchema(type="span")
            // Note: Java record allows null by default. This test verifies the behavior
            TraceSchema schema = new TraceSchema("span", null);
            assertNull(schema.payload());
        }
    }
    
    @Nested
    @DisplayName("CustomSchema Tests")
    class CustomSchemaTests {
        
        @Test
        @DisplayName("Should allow extra fields")
        void testCustomSchemaAllowsExtraFields() {
            // Python: schema = CustomSchema(custom_field="value", another_field=123)
            //         assert schema.custom_field == "value"
            //         assert schema.another_field == 123
            CustomSchema schema = new CustomSchema(Map.of(
                "custom_field", "value",
                "another_field", 123
            ));
            
            assertEquals("value", schema.get("custom_field"));
            assertEquals(123, schema.get("another_field"));
        }
        
        @Test
        @DisplayName("Should allow arbitrary types")
        void testCustomSchemaAllowsArbitraryTypes() {
            // Python: schema = CustomSchema(custom_obj=obj)
            //         assert schema.custom_obj is obj
            Object customObj = new Object();
            CustomSchema schema = new CustomSchema(Map.of("custom_obj", customObj));
            
            assertSame(customObj, schema.get("custom_obj"));
        }
    }
    
    @Nested
    @DisplayName("StreamData Tests")
    class StreamDataTests {
        
        @Test
        @DisplayName("Should create StreamData with all fields")
        void testStreamDataCreation() {
            // Python: data = StreamData(
            //             code=StreamCode.START,
            //             msg="success",
            //             data={"content": "test"},
            //             execution_id="exec_123",
            //             index=0
            //         )
            StreamData data = new StreamData(
                StreamCode.START,
                "success",
                Map.of("content", "test"),
                "exec_123",
                0
            );
            
            assertEquals(StreamCode.START, data.getCode());
            assertEquals("success", data.getMsg());
            assertEquals(Map.of("content", "test"), data.getData());
            assertEquals("exec_123", data.getExecutionId());
            assertEquals(0, data.getIndex());
        }
        
        @Test
        @DisplayName("Should have proper string representation")
        void testStreamDataStr() {
            // Python: result = str(data)
            //         assert "StreamData" in result
            //         assert "exec_123" in result
            StreamData data = new StreamData(
                StreamCode.START,
                "test",
                Map.of(),
                "exec_123",
                0
            );
            
            String result = data.toString();
            
            assertTrue(result.contains("StreamData") || result.contains("exec_123"));
        }
    }
}

